package com.langlez.matching.application

import com.langlez.block.contract.BlockReader
import com.langlez.core.cache.Cache
import com.langlez.core.cache.CacheProvider
import com.langlez.follow.contract.FollowReader
import com.langlez.lang.contract.LanguageReader
import com.langlez.lang.contract.LanguageReader.LanguageInfo
import com.langlez.lang.contract.LanguageReader.Level
import com.langlez.lang.contract.LanguageReader.Role
import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.OnlineTracker
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.support.StaticMessageSource
import java.util.Locale

/**
 * 필터가 하나라도 빠지면 차단한 상대나 탈퇴 회원이 추천에 뜬다. 전부 조용히 통과하는 종류라
 * 여기서 각각 고정한다.
 */
class MatchingServiceTest : BehaviorSpec({

    val langs = mockk<LanguageReader>()
    val members = mockk<MemberReader>()
    val blocks = mockk<BlockReader>()
    val follows = mockk<FollowReader>()
    val tracker = mockk<OnlineTracker>()

    val messages = StaticMessageSource().apply {
        addMessage("lang.required", Locale.KOREAN, "먼저 모국어와 배우는 언어를 등록해 주세요.")
    }

    /**
     * 진짜 맵을 쓴다. 목으로 두면 "순서를 캐시하고 접속만 다시 읽는다"는 이 서비스의 핵심 동작이
     * 아무것도 검증되지 않는다.
     */
    val stored = mutableMapOf<Any, Any>()
    val cache = object : Cache {
        override fun <T : Any> get(key: Any, type: Class<T>): T? =
            stored[key]?.takeIf(type::isInstance)?.let(type::cast)

        override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>) = emptyMap<Any, T>()
        override fun put(key: Any, value: Any) { stored[key] = value }
        override fun <T : Any> putMany(entries: Map<out Any, T>) = Unit
        override fun putIfAbsent(key: Any, value: Any) { stored.putIfAbsent(key, value) }
        override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) = Unit
        override fun evict(key: Any) { stored.remove(key) }
        override fun evictMany(keys: Collection<Any>) = keys.forEach(stored::remove)
    }
    val caches = mockk<CacheProvider>().also { every { it.getCache(any()) } returns cache }

    val service = MatchingService(langs, members, blocks, follows, tracker, MatchScorer(), messages, caches)

    afterEach {
        clearMocks(langs, members, blocks, follows, tracker, answers = false)
        stored.clear()
    }

    val me = 1L
    val mine = listOf(
        LanguageInfo("ko", Role.NATIVE, null),
        LanguageInfo("en", Role.LEARNING, Level.INTERMEDIATE),
    )
    val theirs = listOf(
        LanguageInfo("en", Role.NATIVE, null),
        LanguageInfo("ko", Role.LEARNING, Level.INTERMEDIATE),
    )

    fun profile(id: Long, status: MemberReader.Status = MemberReader.Status.ACTIVE) = MemberReader.ProfileInfo(
        id = id,
        handle = "user$id",
        gender = "SECRET",
        locale = null,
        birthDay = null,
        status = status,
    )

    Given("모국어나 학습언어를 등록하지 않았으면") {
        every { langs.languagesOf(me) } returns listOf(LanguageInfo("ko", Role.NATIVE, null))

        val response = service.recommend(me, size = 20, offset = 0, refresh = false, locale = Locale.KOREAN)

        Then("빈 목록과 안내 문구가 온다. 에러가 아니다") {
            response.members shouldBe emptyList()
            response.hasNext shouldBe false
            response.message!!.shouldNotBeEmpty()
        }

        Then("안내 문구는 i18n 키가 아니라 번역된 문장이다") {
            response.message shouldBe "먼저 모국어와 배우는 언어를 등록해 주세요."
        }

        Then("후보 질의를 아예 돌리지 않는다") {
            verify(exactly = 0) { langs.complementaryCandidates(any(), any(), any(), any()) }
        }
    }

    Given("차단·이미 팔로우·정지·탈퇴가 섞인 후보가 나오면") {
        val normal = 2L
        val blocked = 3L
        val following = 4L
        val withdrawn = 5L
        val suspended = 6L
        val created = 7L
        val all = listOf(normal, blocked, following, withdrawn, suspended, created)

        every { langs.languagesOf(me) } returns mine
        every { langs.complementaryCandidates(setOf("ko"), setOf("en"), me, 500) } returns all
        every { blocks.blockedAmong(me, all) } returns setOf(blocked)
        every { follows.followingIds(me) } returns listOf(following)
        every { members.findProfileInfos(listOf(normal, withdrawn, suspended, created)) } returns mapOf(
            normal to profile(normal),
            withdrawn to profile(withdrawn, MemberReader.Status.WITHDRAWN),
            suspended to profile(suspended, MemberReader.Status.SUSPENDED),
            created to profile(created, MemberReader.Status.CREATED),
        )
        every { langs.languagesOf(listOf(normal, created)) } returns mapOf(normal to theirs, created to theirs)
        every { tracker.checkOnline(listOf(normal, created)) } returns mapOf(normal to true, created to false)
        every { members.findProfileInfos(listOf(normal, created)) } returns mapOf(
            normal to profile(normal),
            created to profile(created),
        )

        val response = service.recommend(me, size = 20, offset = 0, refresh = false, locale = Locale.KOREAN)

        Then("차단한 상대는 빠진다") {
            response.members.map { it.id } shouldNotContain blocked
        }

        Then("이미 팔로우한 상대는 빠진다") {
            response.members.map { it.id } shouldNotContain following
        }

        Then("탈퇴·정지 회원은 빠진다") {
            response.members.map { it.id } shouldNotContain withdrawn
            response.members.map { it.id } shouldNotContain suspended
        }

        // Member.verify() 의 프로덕션 호출자가 0건이라 실사용 회원이 전부 CREATED 다.
        // ACTIVE 허용 목록으로 뒤집으면 여기가 빈 목록이 되면서 추천이 통째로 죽는다.
        Then("CREATED 회원은 남는다") {
            response.members.map { it.id } shouldContainExactly listOf(normal, created)
        }

        Then("추천 근거(matchedPairs)를 함께 내려준다") {
            response.members.first().matchedPairs.map { it.theirNative } shouldBe listOf("en")
        }

        Then("접속 상태를 실어 보낸다") {
            response.members.associate { it.id to it.online } shouldBe mapOf(normal to true, created to false)
        }
    }

    Given("후보가 한 명도 없으면") {
        every { langs.languagesOf(me) } returns mine
        every { langs.complementaryCandidates(setOf("ko"), setOf("en"), me, 500) } returns emptyList()

        val response = service.recommend(me, size = 20, offset = 0, refresh = false, locale = Locale.KOREAN)

        Then("빈 목록이다. 에러도 안내 문구도 아니다") {
            response.members shouldBe emptyList()
            response.hasNext shouldBe false
            response.message shouldBe null
        }

        Then("빈 결과도 캐시해 다음 요청이 파이프라인을 다시 돌지 않는다") {
            service.recommend(me, size = 20, offset = 0, refresh = false, locale = Locale.KOREAN)
            service.recommend(me, size = 20, offset = 0, refresh = false, locale = Locale.KOREAN)

            verify(exactly = 1) { langs.complementaryCandidates(any(), any(), any(), any()) }
        }
    }

    Given("한 번 뽑은 뒤 다시 요청하면") {
        val ids = (10L..14L).toList()

        every { langs.languagesOf(me) } returns mine
        every { langs.complementaryCandidates(setOf("ko"), setOf("en"), me, 500) } returns ids
        every { blocks.blockedAmong(me, ids) } returns emptySet()
        every { follows.followingIds(me) } returns emptyList()
        every { members.findProfileInfos(any()) } answers {
            firstArg<Collection<Long>>().associateWith { profile(it) }
        }
        every { langs.languagesOf(any<Collection<Long>>()) } answers {
            firstArg<Collection<Long>>().associateWith { theirs }
        }
        every { tracker.checkOnline(any<Collection<Long>>()) } answers {
            firstArg<Collection<Long>>().associateWith { false }
        }

        val first = service.recommend(me, size = 2, offset = 0, refresh = false, locale = Locale.KOREAN)

        Then("첫 페이지 뒤에 더 있으면 hasNext 가 true 다") {
            first.members.map { it.id } shouldBe listOf(10L, 11L)
            first.hasNext shouldBe true
        }

        Then("offset 으로 다음 페이지를 자른다") {
            val second = service.recommend(me, size = 2, offset = 2, refresh = false, locale = Locale.KOREAN)
            second.members.map { it.id } shouldBe listOf(12L, 13L)
            second.hasNext shouldBe true
        }

        Then("마지막 페이지면 hasNext 가 false 다") {
            service.recommend(me, size = 2, offset = 4, refresh = false, locale = Locale.KOREAN)
                .hasNext shouldBe false
        }

        Then("후보 질의는 캐시 때문에 한 번만 돈다") {
            service.recommend(me, size = 2, offset = 0, refresh = false, locale = Locale.KOREAN)
            service.recommend(me, size = 2, offset = 2, refresh = false, locale = Locale.KOREAN)

            verify(exactly = 1) { langs.complementaryCandidates(any(), any(), any(), any()) }
        }

        Then("캐시에는 Long 이 아니라 문자열이 들어간다") {
            service.recommend(me, size = 2, offset = 0, refresh = false, locale = Locale.KOREAN)

            // JSON 코덱이 작은 수를 Integer 로 되돌려 contains(1L) 이 조용히 false 가 되는 함정을 피한다.
            (stored.getValue(me) as List<*>).all { it is String } shouldBe true
        }

        Then("접속 상태는 캐시하지 않고 매 요청 다시 읽는다") {
            service.recommend(me, size = 2, offset = 0, refresh = false, locale = Locale.KOREAN)
            verify(atLeast = 2) { tracker.checkOnline(any<Collection<Long>>()) }
        }

        Then("refresh=true 면 캐시를 버리고 다시 뽑는다") {
            service.recommend(me, size = 2, offset = 0, refresh = false, locale = Locale.KOREAN)
            service.recommend(me, size = 2, offset = 0, refresh = true, locale = Locale.KOREAN)

            verify(exactly = 2) { langs.complementaryCandidates(any(), any(), any(), any()) }
        }
    }

    Given("MatchingReader 포트로 물으면") {
        val ids = listOf(21L, 22L, 23L)

        every { langs.languagesOf(me) } returns mine
        every { langs.complementaryCandidates(setOf("ko"), setOf("en"), me, 500) } returns ids
        every { blocks.blockedAmong(me, ids) } returns emptySet()
        every { follows.followingIds(me) } returns emptyList()
        every { members.findProfileInfos(ids) } returns ids.associateWith { profile(it) }
        every { langs.languagesOf(ids) } returns ids.associateWith { theirs }
        every { tracker.checkOnline(ids) } returns ids.associateWith { false }

        Then("limit 만큼 잘린 id 목록이 나온다") {
            service.recommendedIds(me, limit = 2) shouldBe listOf(21L, 22L)
        }

        Then("limit 이 0 이하면 조회조차 하지 않는다") {
            service.recommendedIds(me, limit = 0) shouldBe emptyList()
            verify(exactly = 0) { langs.languagesOf(me) }
        }
    }
})
