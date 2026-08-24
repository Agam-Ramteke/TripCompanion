package com.tripcompanion.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history.
 *
 * Migrations live here rather than in the Hilt module so that migration tests
 * can exercise them without standing up a dependency graph. Every version bump
 * needs an entry here and a matching exported schema under `app/schemas`.
 *
 * 1 → 2  Added `events.whatWeAreDoing` and the provider columns on `locations`.
 * 2 → 3  Stripped the photography-assignment columns off `planned_photos` (§15)
 *        and made a photo's title optional.
 * 3 → 4  Added the train feature (`trains`, `train_stops`, `train_run_status`,
 *        `train_run_stops`), hotel paperwork (`stay_details`), trip cover photos,
 *        and the place columns behind the Places tabs.
 * 4 → 5  Made a booking cover a party: `train_passengers` replaces the single
 *        `coach`/`seat` pair on `trains`, which also gained the fields an IRCTC
 *        e-ticket carries.
 * 5 → 6  Added `events.backgroundImageUri`: an optional photo the user picks for an
 *        activity, shown behind its Home next-up card (§14).
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE events ADD COLUMN whatWeAreDoing TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE locations ADD COLUMN providerPlaceId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE locations ADD COLUMN providerName TEXT DEFAULT NULL")
        }
    }

    /**
     * A planned photo is an image with an optional label, so `description`,
     * `poseOrShotDescription` and `status` are dropped. SQLite on API 26 has no
     * DROP COLUMN, so the table is rebuilt and the surviving columns copied.
     *
     * The dropped text is not merged into the title: a photo the user labelled
     * "Palace doorway" should stay "Palace doorway", not become a paragraph.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `planned_photos_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `eventId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `referenceImageUri` TEXT,
                    `order` INTEGER NOT NULL,
                    FOREIGN KEY(`eventId`) REFERENCES `events`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `planned_photos_new` (`id`, `eventId`, `title`, `referenceImageUri`, `order`)
                SELECT `id`, `eventId`, `title`, `referenceImageUri`, `order` FROM `planned_photos`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `planned_photos`")
            db.execSQL("ALTER TABLE `planned_photos_new` RENAME TO `planned_photos`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_planned_photos_eventId` " +
                    "ON `planned_photos` (`eventId`)"
            )
        }
    }

    /**
     * Trains, hotel paperwork, and the columns behind trip covers and the Places tabs.
     *
     * All additive: five new tables and seven new columns, nothing dropped, so no table
     * rebuild is needed this time. Existing rows keep every value they had.
     *
     * The new columns are added with defaults that mean "not set" rather than "false-ish":
     * a place that predates this version has no photo and no rating, and it is shown as
     * unrated rather than as rated zero.
     *
     * Every `CREATE TABLE` here has to match what Room generates for the corresponding
     * entity down to the column order, the `NOT NULL`s and the foreign-key clauses, or
     * validation fails at open time on an upgraded install. `MigrationTest` runs a populated
     * v3 database through this and then lets Room validate the result, which is the only way
     * to know that it does.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── Trains ──

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trains` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tripId` INTEGER NOT NULL,
                    `eventId` INTEGER,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `originCode` TEXT NOT NULL,
                    `originName` TEXT NOT NULL,
                    `destinationCode` TEXT NOT NULL,
                    `destinationName` TEXT NOT NULL,
                    `departureTime` TEXT NOT NULL,
                    `arrivalTime` TEXT NOT NULL,
                    `travelClass` TEXT NOT NULL,
                    `coach` TEXT NOT NULL,
                    `seat` TEXT NOT NULL,
                    `pnr` TEXT NOT NULL,
                    `bookingStatus` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `knownDelayMinutes` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trains_tripId` ON `trains` (`tripId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `train_stops` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trainId` INTEGER NOT NULL,
                    `serialNo` INTEGER NOT NULL,
                    `stationCode` TEXT NOT NULL,
                    `stationName` TEXT NOT NULL,
                    `scheduledArrival` TEXT,
                    `scheduledDeparture` TEXT,
                    `distanceKm` INTEGER NOT NULL,
                    `dayOffset` INTEGER NOT NULL,
                    FOREIGN KEY(`trainId`) REFERENCES `trains`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_train_stops_trainId` " +
                    "ON `train_stops` (`trainId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `train_run_status` (
                    `trainId` INTEGER NOT NULL,
                    `fetchedAt` TEXT NOT NULL,
                    `runDate` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `currentStationCode` TEXT NOT NULL,
                    `currentStationName` TEXT NOT NULL,
                    `delayMinutes` INTEGER NOT NULL,
                    `lastDepartedSerial` INTEGER NOT NULL,
                    `progressFraction` REAL NOT NULL,
                    `nextStopCode` TEXT NOT NULL,
                    `nextStopName` TEXT NOT NULL,
                    `nextStopEta` TEXT,
                    `averageSpeedKmph` REAL,
                    `message` TEXT NOT NULL,
                    PRIMARY KEY(`trainId`),
                    FOREIGN KEY(`trainId`) REFERENCES `trains`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `train_run_stops` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trainId` INTEGER NOT NULL,
                    `serialNo` INTEGER NOT NULL,
                    `stationCode` TEXT NOT NULL,
                    `stationName` TEXT NOT NULL,
                    `scheduledArrival` TEXT,
                    `actualArrival` TEXT,
                    `scheduledDeparture` TEXT,
                    `actualDeparture` TEXT,
                    `arrivalDelayMinutes` INTEGER,
                    `departureDelayMinutes` INTEGER,
                    `distanceKm` INTEGER NOT NULL,
                    `dayOffset` INTEGER NOT NULL,
                    `isDeparted` INTEGER NOT NULL,
                    `isCurrent` INTEGER NOT NULL,
                    FOREIGN KEY(`trainId`) REFERENCES `trains`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_train_run_stops_trainId` " +
                    "ON `train_run_stops` (`trainId`)"
            )

            // ── Hotel paperwork ──

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stay_details` (
                    `eventId` INTEGER NOT NULL,
                    `bookingReference` TEXT NOT NULL,
                    `roomType` TEXT NOT NULL,
                    `guests` INTEGER NOT NULL,
                    `contactPhone` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `checkInInstructions` TEXT NOT NULL,
                    `photoUri` TEXT,
                    PRIMARY KEY(`eventId`),
                    FOREIGN KEY(`eventId`) REFERENCES `events`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // ── Photos and place state ──

            db.execSQL("ALTER TABLE trips ADD COLUMN coverImageUri TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE locations ADD COLUMN photoUri TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE locations ADD COLUMN rating REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE locations ADD COLUMN openingHours TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE locations ADD COLUMN estimatedVisitMinutes INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE locations ADD COLUMN isVisited INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE locations ADD COLUMN isSaved INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * A booking becomes a party.
     *
     * `trains.coach` and `trains.seat` described one person, which was wrong the first time two
     * people travelled on one PNR — the railway allots a berth per passenger and routinely gives
     * a party different berths and occasionally different coaches. They move to
     * `train_passengers`, and `trains` gains the rest of what an e-ticket prints.
     *
     * SQLite on API 26 has no DROP COLUMN, so `trains` is rebuilt. Three details make that safe:
     *
     * - The seed of `train_passengers` runs **before** the old table goes, because it reads the
     *   very columns being dropped.
     * - Every id is preserved by the copy, so `train_stops`, `train_run_status` and
     *   `train_run_stops` still point at their trains afterwards.
     * - Room runs migrations before it issues `PRAGMA foreign_keys = ON`, so dropping the parent
     *   table does not cascade into those children. `MigrationTest` asserts that rather than
     *   trusting it.
     *
     * Existing trains keep their allotment as a single unnamed passenger. A name is not invented:
     * the old schema never held one, and a row reading "Passenger 1" would be a fabrication the
     * user then has to correct. Trains with nothing allotted and no status get no passenger row
     * at all, which is the honest representation of a train someone had merely planned.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `train_passengers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trainId` INTEGER NOT NULL,
                    `serialNo` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `age` INTEGER,
                    `gender` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `coach` TEXT NOT NULL,
                    `berth` TEXT NOT NULL,
                    `berthType` TEXT NOT NULL,
                    `queuePosition` INTEGER,
                    `queueKind` TEXT NOT NULL,
                    `bookingStatusText` TEXT NOT NULL,
                    `currentStatusText` TEXT NOT NULL,
                    FOREIGN KEY(`trainId`) REFERENCES `trains`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_train_passengers_trainId` " +
                    "ON `train_passengers` (`trainId`)"
            )

            // Carry each existing allotment over as one passenger, while the columns still exist.
            db.execSQL(
                """
                INSERT INTO `train_passengers` (
                    `trainId`, `serialNo`, `name`, `age`, `gender`, `status`,
                    `coach`, `berth`, `berthType`, `queuePosition`, `queueKind`,
                    `bookingStatusText`, `currentStatusText`
                )
                SELECT `id`, 1, '', NULL, 'UNSPECIFIED', `bookingStatus`,
                       `coach`, `seat`, 'UNKNOWN', NULL, '', '', ''
                FROM `trains`
                WHERE `coach` != '' OR `seat` != '' OR `bookingStatus` != 'NOT_BOOKED'
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trains_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tripId` INTEGER NOT NULL,
                    `eventId` INTEGER,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `originCode` TEXT NOT NULL,
                    `originName` TEXT NOT NULL,
                    `destinationCode` TEXT NOT NULL,
                    `destinationName` TEXT NOT NULL,
                    `departureTime` TEXT NOT NULL,
                    `arrivalTime` TEXT NOT NULL,
                    `travelClass` TEXT NOT NULL,
                    `pnr` TEXT NOT NULL,
                    `bookingStatus` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `knownDelayMinutes` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `quota` TEXT NOT NULL,
                    `distanceKm` INTEGER NOT NULL,
                    `boardingCode` TEXT NOT NULL,
                    `boardingName` TEXT NOT NULL,
                    `bookedAt` TEXT,
                    `agentName` TEXT NOT NULL,
                    `agentBookingId` TEXT NOT NULL,
                    `transactionId` TEXT NOT NULL,
                    `fareTicket` REAL,
                    `fareConvenience` REAL,
                    `fareInsurance` REAL,
                    `fareAgentService` REAL,
                    `farePaymentGateway` REAL,
                    `fareTotal` REAL,
                    `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `trains_new` (
                    `id`, `tripId`, `eventId`, `number`, `name`,
                    `originCode`, `originName`, `destinationCode`, `destinationName`,
                    `departureTime`, `arrivalTime`, `travelClass`, `pnr`, `bookingStatus`,
                    `platform`, `knownDelayMinutes`, `notes`,
                    `quota`, `distanceKm`, `boardingCode`, `boardingName`, `bookedAt`,
                    `agentName`, `agentBookingId`, `transactionId`,
                    `fareTicket`, `fareConvenience`, `fareInsurance`,
                    `fareAgentService`, `farePaymentGateway`, `fareTotal`,
                    `createdAt`, `updatedAt`
                )
                SELECT `id`, `tripId`, `eventId`, `number`, `name`,
                       `originCode`, `originName`, `destinationCode`, `destinationName`,
                       `departureTime`, `arrivalTime`, `travelClass`, `pnr`, `bookingStatus`,
                       `platform`, `knownDelayMinutes`, `notes`,
                       '', 0, '', '', NULL,
                       '', '', '',
                       NULL, NULL, NULL,
                       NULL, NULL, NULL,
                       `createdAt`, `updatedAt`
                FROM `trains`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `trains`")
            db.execSQL("ALTER TABLE `trains_new` RENAME TO `trains`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trains_tripId` ON `trains` (`tripId`)")
        }
    }

    /**
     * An activity gains a photo for its Home card (§14, Task 3).
     *
     * The column is nullable and additive, so a plain `ALTER TABLE` is enough — no rebuild, and
     * every existing event keeps its data and simply has no chosen background yet. Null is the
     * honest value: a plan made before this feature never picked one.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE events ADD COLUMN backgroundImageUri TEXT DEFAULT NULL")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
