package com.mnemocards.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Колода (change-007): идентификатор — UUID, сгенерированный на устройстве.
 * Числовые автоинкрементные PK уникальны только внутри одного устройства
 * и коллизируют при синхронизации, поэтому схема v4 переводит записи
 * на String-идентификаторы.
 *
 * Поля подготовки к синхронизации:
 *  - [isChanged] — запись изменена локально и ещё не отправлена на сервер;
 *  - [revision] — локальный счётчик правок, +1 на каждое изменение;
 *  - [isDeleted] — мягкое удаление: строка остаётся в БД до отправки
 *    на сервер и скрывается из всех запросов для UI.
 *
 * defaultValue совпадают с DDL миграции MIGRATION_3_4, иначе Room считает
 * схему после миграции несовместимой.
 */
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "1")
    val isChanged: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val revision: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
)
