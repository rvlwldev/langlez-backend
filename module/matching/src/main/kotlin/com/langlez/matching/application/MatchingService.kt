package com.langlez.matching.application

import com.langlez.block.contract.BlockReader
import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.follow.contract.FollowReader
import com.langlez.lang.contract.LanguageReader
import com.langlez.lang.contract.LanguageReader.LanguageInfo
import com.langlez.lang.contract.LanguageReader.Role
import com.langlez.matching.api.response.MatchingMemberResponse
import com.langlez.matching.api.response.MatchingMembersResponse
import com.langlez.matching.contract.MatchingReader
import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.OnlineTracker
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * 언어 기반 상호보완 추천.
 *
 * **이 모듈에는 `domain`/`infrastructure` 계층이 없다.** 4계층 규약에서 벗어나는 유일한 지점인데,
 * matching 은 자기 데이터가 하나도 없는 **조합 모듈**이기 때문이다. 엔티티도 테이블도 저장소 포트도
 * 없으니 `domain` 에 넣을 것이 없고, 그러면 `infrastructure` 가 구현할 대상도 없다. 계층을 억지로
 * 만들면 빈 패키지와 통과 전용 인터페이스만 남는다. 데이터를 갖게 되는 날 그때 만든다.
 *
 * **`@Transactional` 을 걸지 않는다.** 자기 테이블이 없어 열 트랜잭션이 없고, 여기서 부르는 것은
 * 전부 `*-api` 포트다. 그 포트들은 곧 gRPC/HTTP 로 대체될 예정이라 트랜잭션 안에 두면
 * DB 커넥션을 쥔 채 네트워크를 기다려 풀이 마른다. 나중에 "누락"으로 보고 붙이지 마라.
 *
 * **언어를 lang 에 두고 추천을 여기 둔 이유:** 매칭 입력이 언어만이 아니다. 차단·접속·팔로우가
 * 들어가고 `interest` 가 예정돼 있다. lang 안에 두면 lang 이 포트 넷을 주입받아
 * "언어만 담당한다"가 그 자리에서 깨진다.
 */
@Service
class MatchingService(
    private val langs: LanguageReader,
    private val members: MemberReader,
    private val blocks: BlockReader,
    private val follows: FollowReader,
    private val tracker: OnlineTracker,
    private val scorer: MatchScorer,
    private val messages: MessageSource,
    caches: CacheProvider,
) : MatchingReader {

    private val candidates = caches.getCache(CACHE_NAME)

    /**
     * 추천 한 페이지.
     *
     * **커서 페이징을 쓸 수 없다.** 점수에 접속 상태가 섞여 있어 커서 기준값이 다음 요청에서 이미
     * 다른 값이다. 같은 사람이 두 페이지에 나오거나 아예 빠진다. 그래서 **정렬된 id 리스트를
     * 통째로 캐시하고 offset 으로 자른다.**
     *
     * 캐시에는 **순서만** 들어간다. 접속 상태는 초 단위로 바뀌는데 후보 리스트는 10분 살기 때문에,
     * 캐시에 담으면 화면의 접속 점이 최대 10분 낡은 값이 된다. 순서는 10분 고정, 점만 실시간이다.
     */
    fun recommend(
        memberId: Long,
        size: Int,
        offset: Int,
        refresh: Boolean,
        locale: Locale,
    ): MatchingMembersResponse {
        val mine = langs.languagesOf(memberId)
        if (!isMatchable(mine)) return MatchingMembersResponse(
            members = emptyList(),
            hasNext = false,
            message = messages.getMessage(LANGUAGE_REQUIRED_KEY, null, locale),
        )

        val ranked = rankedIds(memberId, mine, refresh)
        val page = ranked.drop(offset).take(size)
        if (page.isEmpty()) return MatchingMembersResponse(emptyList(), hasNext = false)

        // 접속만 여기서 다시 읽는다. 순서는 캐시된 것이라 최대 10분 낡았어도 되지만,
        // 화면에 찍히는 접속 점까지 낡으면 사용자가 없는 사람에게 말을 건다.
        val online = tracker.checkOnline(page)
        val profiles = members.findProfileInfos(page)
        val languages = langs.languagesOf(page)

        val items = page.mapNotNull { id ->
            // 랭킹 이후 탈퇴·정지된 회원이면 여기서 빠진다. **null 검사만으로는 못 거른다** —
            // 이 저장소는 탈퇴해도 members 행을 지우지 않아 findProfileInfos 가
            // status = WITHDRAWN 으로 정상 반환한다. 순서는 10분 캐시라 그 창 안에 상태가 바뀐다.
            // rank() 와 같은 EXCLUDED 를 본다. 두 곳에 따로 두면 한쪽만 고치는 사고가 난다.
            // hasNext 는 캐시된 전체 길이로 계산하므로 이 페이지가 size 보다 짧아도 다음 페이지는 이어진다.
            val profile = profiles[id]?.takeIf { it.status !in EXCLUDED } ?: return@mapNotNull null
            val theirs = languages[id].orEmpty()
            MatchingMemberResponse(
                member = profile,
                online = online[id] == true,
                languages = theirs,
                matchedPairs = scorer.matchedPairs(mine, theirs),
            )
        }

        return MatchingMembersResponse(items, hasNext = offset + size < ranked.size)
    }

    override fun recommendedIds(memberId: Long, limit: Int): List<Long> {
        if (limit <= 0) return emptyList()
        val mine = langs.languagesOf(memberId)
        if (!isMatchable(mine)) return emptyList()

        return rankedIds(memberId, mine, refresh = false).take(limit)
    }

    /**
     * 캐시된 순서를 읽거나 없으면 새로 뽑는다.
     *
     * **id 를 `Long` 이 아니라 문자열로 담는다.** 공용 JSON 코덱이 작은 수를 `Integer` 로 되돌려
     * `contains(1L)` 이 조용히 false 가 된다. `MemberOnlineTracker.recordViewing` 이 같은 이유로
     * 문자열을 쓴다. 이 저장소가 실제로 당한 함정이라 되돌리지 마라.
     *
     * 후보가 0명이어도 빈 리스트를 그대로 캐시한다. `null` 로 두면 후보 없는 회원이 새로고침할
     * 때마다 전체 파이프라인을 다시 돈다.
     */
    private fun rankedIds(memberId: Long, mine: List<LanguageInfo>, refresh: Boolean): List<Long> {
        if (refresh) candidates.evict(memberId)

        candidates.get<List<*>>(memberId)
            ?.let { cached -> return cached.mapNotNull { (it as? String)?.toLongOrNull() } }

        return rank(memberId, mine).also { ids -> candidates.put(memberId, ids.map(Long::toString)) }
    }

    private fun rank(memberId: Long, mine: List<LanguageInfo>): List<Long> {
        val ids = langs.complementaryCandidates(
            myNativeLanguages = languagesOf(mine, Role.NATIVE),
            myLearningLanguages = languagesOf(mine, Role.LEARNING),
            excludeMemberId = memberId,
            limit = CANDIDATE_LIMIT,
        )
        if (ids.isEmpty()) return emptyList()

        // 배치 포트로 한 번에 묻는다. 항목마다 단건 조회를 돌면 이 포트들이 원격이 될 때
        // 후보 수만큼 왕복이 생긴다.
        val blocked = blocks.blockedAmong(memberId, ids)
        val following = follows.followingIds(memberId).toSet()
        val visible = ids.filterNot { it in blocked || it in following }
        if (visible.isEmpty()) return emptyList()

        val profiles = members.findProfileInfos(visible)
        // 허용 목록(ACTIVE 만 통과)으로 뒤집지 마라. Member.verify() 의 프로덕션 호출자가 0건이라
        // 실사용 회원이 전부 영구히 CREATED 다 — ACTIVE 만 남기면 컴파일도 테스트도 통과한 채
        // 런타임에 추천이 항상 0건이 된다. 제외 목록으로 둔다.
        val alive = visible.filter { id -> profiles[id]?.status?.let { it !in EXCLUDED } == true }
        if (alive.isEmpty()) return emptyList()

        return scorer.rank(mine, langs.languagesOf(alive), tracker.checkOnline(alive))
    }

    /** 모국어와 학습언어가 둘 다 있어야 상호보완이 성립한다. 한쪽만 등록한 회원은 후보를 못 만든다. */
    private fun isMatchable(mine: List<LanguageInfo>): Boolean =
        mine.any { it.role == Role.NATIVE } && mine.any { it.role == Role.LEARNING }

    private fun languagesOf(languages: List<LanguageInfo>, role: Role): Set<String> =
        languages.filter { it.role == role }.mapTo(mutableSetOf()) { it.language }

    companion object {
        /** `RedisCache` 가 `"{이름}:{키}"` 로 인코딩하므로 실제 키는 `match:candidates:{회원id}` 다. */
        private const val CACHE_NAME = "match:candidates"

        /** DB 부하 상한이지 페이지 크기가 아니다. 랭킹·필터가 이 안에서 돈다. */
        private const val CANDIDATE_LIMIT = 500

        private const val LANGUAGE_REQUIRED_KEY = "lang.required"

        /**
         * 추천에서 빼는 상태. **[rank] 와 [recommend] 가 같은 것을 본다** — 랭킹 시점과 페이지 조회
         * 시점 사이에 10분 캐시 창이 있어 양쪽에서 각각 걸러야 하고, 둘로 나누면 한쪽만 고치게 된다.
         */
        private val EXCLUDED = setOf(MemberReader.Status.SUSPENDED, MemberReader.Status.WITHDRAWN)
    }
}
