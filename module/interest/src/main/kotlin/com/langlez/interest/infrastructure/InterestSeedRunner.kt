package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import com.langlez.interest.infrastructure.jpa.InterestJpaRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** 기동 시 기본 관심사 목록을 멱등하게 시드한다. en 컬럼 값 기준으로 존재 여부를 확인한다. */
@Component
class InterestSeedRunner(private val jpa: InterestJpaRepository) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        SEED_DATA.forEach { seed ->
            val exists = jpa.findAll().any { it.en == seed.en }
            if (!exists) {
                jpa.save(
                    Interest(
                        ko = seed.ko, en = seed.en, ja = seed.ja, zhTW = seed.zhTW, zhCN = seed.zhCN,
                        de = seed.de, vi = seed.vi, ind = seed.ind, fr = seed.fr, pt = seed.pt,
                        es = seed.es, ru = seed.ru,
                    )
                )
            }
        }
    }

    private data class Seed(
        val ko: String, val en: String, val ja: String, val zhTW: String, val zhCN: String,
        val de: String, val vi: String, val ind: String, val fr: String, val pt: String,
        val es: String, val ru: String,
    )

    companion object {
        private val SEED_DATA = listOf(
            Seed("여행", "Travel", "旅行", "旅行", "旅行", "Reisen", "Du lịch", "Perjalanan", "Voyage", "Viagem", "Viajar", "Путешествия"),
            Seed("영화", "Movies", "映画", "電影", "电影", "Filme", "Phim ảnh", "Film", "Cinéma", "Filmes", "Películas", "Кино"),
            Seed("음악", "Music", "音楽", "音樂", "音乐", "Musik", "Âm nhạc", "Musik", "Musique", "Música", "Música", "Музыка"),
            Seed("운동", "Sports", "スポーツ", "運動", "运动", "Sport", "Thể thao", "Olahraga", "Sport", "Esportes", "Deportes", "Спорт"),
            Seed("독서", "Reading", "読書", "閱讀", "阅读", "Lesen", "Đọc sách", "Membaca", "Lecture", "Leitura", "Lectura", "Чтение"),
            Seed("요리", "Cooking", "料理", "烹飪", "烹饪", "Kochen", "Nấu ăn", "Memasak", "Cuisine", "Culinária", "Cocina", "Готовка"),
            Seed("사진", "Photography", "写真", "攝影", "摄影", "Fotografie", "Nhiếp ảnh", "Fotografi", "Photographie", "Fotografia", "Fotografía", "Фотография"),
            Seed("등산", "Hiking", "ハイキング", "健行", "徒步", "Wandern", "Đi bộ đường dài", "Mendaki", "Randonnée", "Caminhada", "Senderismo", "Пеший туризм"),
        )
    }
}
