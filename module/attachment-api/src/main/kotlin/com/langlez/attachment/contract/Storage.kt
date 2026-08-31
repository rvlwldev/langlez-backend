package com.langlez.attachment.contract

/**
 * 1. 클라이언트가 업로드 요청 (멤버ID, 도메인, 타입, 원래 파일네임) : Presigned URL 반환
 *   - 해당 Presigned URL로 클라이언트가 파일 업로드
 * 2. 클라이언트의 S3 업로드 완료 요청(멤버ID, Key)
 * 3. key로 업로드 확인 후 조회용 URL 반환, DB에 첨부파일 이력 저장
 * 4. 클라이언트가 해당 파일을 조회용 URL로 조회 가능
 */
interface Storage {
    fun presign(id: Long, source: String, type: Type, filename: String): PresignedResult
    fun attach(key: String, sourceId: Long? = null): String

    enum class Type { IMAGE, VIDEO, AUDIO }
    data class PresignedResult(val key: String, val presigned: String)
}
