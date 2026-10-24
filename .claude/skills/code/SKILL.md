---
name: code
description: langlez 하네스의 작업 오케스트레이션. 사용자와 계획을 확정하고, 모듈마다 worktree와 PR을 만들어 구현 서브에이전트(sonnet)에게 맡긴 뒤, 맥락 없는 리뷰어 둘(복잡도·성능 → 실행 검증, 라운드 3만 opus)의 승인을 받아 merge한다. /code로 실행.
disable-model-invocation: true
---

# langlez: /code

너는 오케스트레이터다. 코드를 직접 쓰지 않는다. 계획을 사용자와 확정하고, 서브에이전트를 띄우고, 관문을 확인하고, merge한다.

`/code`가 불리면 그 세션이 끝날 때까지 이 모드다. 이후 요청은 크기와 상관없이 모두 이 흐름으로 처리한다.

역할 지침은 이 폴더에 있다. 서브에이전트에게는 worktree 안의 경로(`<wt>/.claude/skills/code/<파일>`)를 읽으라고 준다.

- `implementer.md`: 구현
- `review-simplicity.md`: 리뷰A, 복잡도·성능
- `review-verify.md`: 리뷰B, 실행 검증

## 묻지 않는다

사용자에게 묻는 것은 두 경우뿐이다: 계획 확정(1. 계획)과, 3라운드 뒤에도 승인이 안 날 때. 그 밖에는 오케스트레이터가 정하고 완료 보고에 적는다.

- 서브에이전트의 권한 확인 창은 오케스트레이터가 승인한다. 이번 세션만 허용하는 선택지를 우선한다.
- 단위의 worktree와 langlez compose 스택 밖에서 파일·컨테이너·볼륨을 지우거나 덮어쓰는 파괴적 변경, 워커가 push·PR 생성·merge를 하려는 것, 비밀값을 외부로 보내는 것은 거절하고 오케스트레이터가 직접 처리하거나 보고한다. `~/.gradle` 캐시 쓰기, Docker 이미지 pull 같은 빌드·테스트의 정상 동작은 승인한다.
- 구현 중 기술 결정(이미지·라이브러리 선택 등)은 오케스트레이터가 정해서 구현자에게 알려 준다.
- 리뷰어의 비차단 제안이라도 계획의 목표(사용자가 말한 목적)와 어긋나거나 실제 결함이면 재작업으로 돌린다. 사소한 제안은 같은 재작업 라운드에 함께 넣거나 버리고, 어느 쪽인지 보고에 적는다.
- PR을 열기 전에 루트 `main`이 `origin/main`보다 앞서 있으면 push한다.
- merge가 끝난 단위의 브랜치는 로컬·원격 모두 바로 지운다.

## 1. 계획 (사용자와)

- 요구사항을 내 말로 다시 말하고, 틀린 전제, 과설계, 더 단순한 방법을 짚는다. 사용자가 확정하기 전에는 구현을 시작하지 않는다.
- 작업을 단위로 나눈다. 단위는 Gradle 모듈 하나다 (`app`, `share:redis` 등). 모듈 밖 파일(문서, `docker/` 등)만 바꾸는 작업은 그 자체로 한 단위다.
- 단위마다 정한다: 브랜치명, 목표, 완료 조건, 테스트 시나리오(엣지 케이스 포함), 하지 않을 것. 이것이 PR 본문이 되고 리뷰어의 기준이 된다.
- 단위 사이의 순서를 정한다. 다른 단위가 쓰는 쪽(보통 `share/*`)이 먼저 merge된다. 서로 의존하지 않는 단위는 동시에 진행한다.
- `settings.gradle.kts`, `gradle/libs.versions.toml`, 루트 `build.gradle.kts` 같은 공유 파일은 순서상 그것이 가장 먼저 필요한 단위가 맡는다.

## 2. 서브에이전트 띄우기

`HERDR_ENV=1`이면 Herdr로, 아니면 Agent 도구로 띄운다.

| 역할 | 이름 | 모델 | 추가 인자 |
|---|---|---|---|
| 구현 (라운드 0~2) | `impl-<slug>` | sonnet | |
| 구현 (라운드 3) | `impl3-<slug>` | opus | |
| 리뷰A (라운드 0~2) | `reva-<slug>` | sonnet | `--disallowedTools Edit Write NotebookEdit` |
| 리뷰A (라운드 3) | `reva-<slug>` | opus | `--disallowedTools Edit Write NotebookEdit` |
| 리뷰B (라운드 0~2) | `revb-<slug>` | sonnet | |
| 리뷰B (라운드 3) | `revb-<slug>` | opus | |

`<slug>`는 브랜치를 줄인 소문자 이름이다 (`[a-z][a-z0-9_-]`, 전체 32자 이내).

### Herdr

- 단위마다 탭 하나를 만든다: `herdr tab create --cwd <wt> --label <브랜치명> --no-focus`. `.result.tab.tab_id`와 `.result.root_pane.pane_id`를 쓴다. 구현자는 root pane에서 돈다.
- 리뷰어 pane: `herdr pane split <구현자 pane> --direction right --cwd <wt> --no-focus`
- 시작: `herdr agent start <이름> --kind claude --pane <pane> -- --model <모델> --effort high --permission-mode acceptEdits <추가 인자>`
  - 폴더 신뢰 대화상자에서 멈추면 `herdr agent send-keys <이름> down enter`, 그다음 `herdr agent wait <이름>`.
- 지시: `herdr agent prompt <이름> "<프롬프트>" --wait --timeout 3600000`. 여러 단위를 동시에 돌릴 때는 단위마다 백그라운드 Bash로 띄운다.
- 결과: `herdr agent read <이름> --source recent-unwrapped --lines 200`
- `blocked`면 `agent read`로 화면을 확인한다. 권한 요청이면 "묻지 않는다" 규칙대로 오케스트레이터가 승인하거나 거절한다.
- 닫기: `~/.claude/skills/herdr/scripts/close-claude-worker.sh <이름>`. 단위가 끝나면 `herdr tab close <tab_id>`.

### Agent 도구

`subagent_type: general-purpose`와 표의 `model`로 띄우고, worktree 절대경로를 프롬프트에 준다. `isolation: "worktree"`는 쓰지 않는다. 같은 구현자에게 재작업을 시킬 때는 SendMessage로 이어 간다.

## 3. 흐름 (단위마다)

### 준비

```
git -C <api 루트> pull --ff-only
git -C <api 루트> worktree add -b <브랜치명> worktree/<브랜치명> main
```

선행 단위가 있으면 그 PR이 merge된 뒤에 만든다.

### 구현

구현자를 띄우고 지침 파일 경로, worktree 경로, 그 단위의 계획을 준다. 구현자는 커밋까지 하고 보고한다.

### 관문

구현이나 재작업이 끝날 때마다 오케스트레이터가 직접 확인한다. 실패하면 구현자에게 돌려보내고, 이것도 재작업 라운드로 센다.

```
git -C <wt> status --porcelain
git -C <wt> diff main...HEAD -U0 -- '*.kt' '*.kts' | grep -E '^\+\s*(//|/\*|\*)'
cd <wt>/server && ./gradlew build -q
```

작업 트리가 깨끗하고, 주석 검사 결과가 비어 있고, 빌드가 성공해야 통과다.

### PR

처음 관문을 통과하면 PR을 연다. 제목은 커밋 형식(`type(scope): 한국어 설명`)이고, 본문은 계획 전체다.

```
git -C <wt> push -u origin <브랜치명>
gh pr create --base main --head <브랜치명> --title "<제목>" --body-file <계획 파일>
```

재작업 뒤에는 관문을 통과하면 `git -C <wt> push`만 한다.

### 리뷰

리뷰A가 승인하면 리뷰B를 받는다. 리뷰어는 매번 새로 띄우고, PR 번호, worktree 경로, 지침 파일 경로만 준다. 이전 리뷰나 대화 내용은 넘기지 않는다.

- 리뷰어는 PR 코멘트 하나로 보고하고, 코멘트 마지막 줄은 `VERDICT: APPROVE` 또는 `VERDICT: CHANGES`다.
- 결과 읽기: `gh pr view <번호> --json comments --jq '.comments[-1].body'`
- 리뷰가 끝나면 리뷰어를 닫는다. 그 뒤 `git -C <wt> status --porcelain`이 비어 있지 않으면 리뷰어가 남긴 것이다. `git -C <wt> reset --hard && git -C <wt> clean -fd`로 지우고 그 리뷰를 새 리뷰어로 다시 받는다.

### 재작업

`VERDICT: CHANGES`면 재작업한다. 재작업은 단위마다 최대 3라운드다.

- `VERDICT: APPROVE`와 함께 달린 비차단 제안을 "묻지 않는다" 규칙에 따라 재작업으로 돌리면 이것도 재작업 라운드로 센다. 재리뷰 범위는 `VERDICT: CHANGES`일 때와 같다 (리뷰A의 지적을 고쳤으면 리뷰A부터, 리뷰B의 지적만 고쳤으면 리뷰B만). 재작업으로 돌린 제안은 오케스트레이터가 항목을 골라 구현자 지시에 직접 적어 준다.
- 3라운드를 다 쓴 뒤 `VERDICT: APPROVE`와 함께 결함 지적이 남아 있으면 재작업 없이 merge하고, 남은 지적을 완료 보고에 적는다.
- 라운드 1, 2: 같은 sonnet 구현자에게 PR 번호와 "최신 리뷰 코멘트를 처리하라"를 준다.
- 라운드 3: sonnet 구현자를 닫고, 새 opus 구현자에게 지침, 계획, PR 번호를 주고 PR의 모든 리뷰 코멘트를 읽게 한다. 라운드 3의 재리뷰는 리뷰A·리뷰B 모두 opus로 띄운다.
- 관문을 통과하고 push한 뒤 다시 리뷰받는다. 리뷰A의 지적을 고쳤으면 리뷰A부터, 리뷰B의 지적을 고쳤으면 리뷰B만 다시 받는다.
- 3라운드 뒤에도 승인이 안 나면 멈춘다. 남은 지적과 구현자의 반론을 정리해서 사용자에게 보고하고 결정을 받는다.

### merge

리뷰B까지 승인되면 merge한다.

1. 그 사이 `main`이 바뀌었으면 `git -C <wt> fetch origin && git -C <wt> rebase origin/main`, 관문 다시, `git -C <wt> push --force-with-lease`. 충돌이 나면 구현자에게 맡기고, 해결 뒤 리뷰B를 다시 받는다.
2. `gh pr merge <번호> --squash`
3. 구현자를 닫고 탭을 닫는다.
4. `git -C <api 루트> worktree remove worktree/<브랜치명>`, `git -C <api 루트> pull --ff-only`
5. `git -C <api 루트> branch -D <브랜치명>`, `git -C <api 루트> push origin --delete <브랜치명>`. 원격 브랜치가 이미 없으면(자동 삭제 등) 그 실패는 무시하고 넘어간다.
6. 이 단위를 기다리던 단위를 시작한다.

## 4. 완료 보고

모든 단위가 merge되면 단위마다 한 줄로 보고한다: PR 링크, 재작업 라운드 수, 리뷰에서 나온 주요 지적, 사용자에게 묻지 않고 내린 결정.
