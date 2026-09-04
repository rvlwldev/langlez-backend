// 계약 모듈. 순수 인터페이스와 DTO 만 담는다.
// core 와 같은 이유로 의존성이 없다 — 어느 모듈이든 순환 없이 물 수 있어야 한다.
plugins {
    alias(libs.plugins.kotlin.jvm)
}
