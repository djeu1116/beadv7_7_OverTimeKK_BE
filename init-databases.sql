CREATE DATABASE IF NOT EXISTS reseat CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER DATABASE reseat CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER DATABASE reseat CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET NAMES utf8mb4;
USE reseat;

-- 아래는 로컬 개발 DB(ddl-auto=update로 유지되던 스키마)에서 mysqldump --no-data로 뜬 스냅샷.
-- ddl-auto=validate로 전환하면서, 빈 DB에 테이블을 먼저 만들어주기 위해 추가함.
-- 이후 엔티티가 바뀌면 이 파일도 같이 갱신해야 함(Hibernate가 더 이상 스키마를 안 건드리므로).

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `email_verified` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `business_name` varchar(255) DEFAULT NULL,
  `business_number` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `settlement_account` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','DORMANT','WITHDRAWN') DEFAULT NULL,
  `user_type` enum('BUSINESS','INDIVIDUAL') DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_app_user_username` (`username`),
  UNIQUE KEY `uk_app_user_email` (`email`),
  UNIQUE KEY `uk_app_user_business_number` (`business_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `hall`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hall` (
  `created_at` datetime(6) NOT NULL,
  `hall_id` bigint NOT NULL AUTO_INCREMENT,
  `modified_at` datetime(6) NOT NULL,
  `seat_total_count` bigint NOT NULL,
  `venue_id` bigint NOT NULL,
  `hall_name` varchar(255) NOT NULL,
  PRIMARY KEY (`hall_id`),
  KEY `FKc2v4ktmjj4raseyspt17o075l` (`venue_id`),
  CONSTRAINT `FKc2v4ktmjj4raseyspt17o075l` FOREIGN KEY (`venue_id`) REFERENCES `venue` (`venue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `idempotency_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `idempotency_key` (
  `idempotency_key` varchar(300) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `request_hash` varchar(255) NOT NULL,
  `response_body` longtext,
  PRIMARY KEY (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint NOT NULL,
  `ticket_price` bigint NOT NULL,
  `hold_key` varchar(255) NOT NULL,
  PRIMARY KEY (`order_item_id`),
  KEY `FKt4dc2r9nbvbujrljv3e23iibt` (`order_id`),
  CONSTRAINT `FKt4dc2r9nbvbujrljv3e23iibt` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_date` date NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `total_amount` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `order_status` enum('PENDING','EXPIRED','PAYMENT_STARTED','COMPLETED','CANCEL_REQUESTED','CANCELLED') NOT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_order_status_expires_at` (`order_status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `amount` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `modified_at` datetime(6) NOT NULL,
  `order_id` bigint NOT NULL,
  `refunded_amount` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `payment_key` varchar(255) DEFAULT NULL,
  `pg_order_id` varchar(255) DEFAULT NULL,
  `payment_status` enum('READY','PAID','FAILED','CONFIRM_PENDING_VERIFICATION','REFUND_PENDING','CANCELLED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmf7n8wo2rwrxsd6f3t9ub2mep` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_refund` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `modified_at` datetime(6) NOT NULL,
  `payment_id` bigint NOT NULL,
  `refund_amount` bigint NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `performance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance` (
  `end_date` date NOT NULL,
  `performance_status` tinyint DEFAULT NULL,
  `start_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `hall_id` bigint NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `performance_id` bigint NOT NULL AUTO_INCREMENT,
  `runtime` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `ticket_open_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `img_path` varchar(255) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`performance_id`),
  CONSTRAINT `performance_chk_1` CHECK ((`performance_status` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `performance_seat_price`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_seat_price` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `modified_at` datetime(6) NOT NULL,
  `performance_id` bigint NOT NULL,
  `price` bigint NOT NULL,
  `zone` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seat_price_performance_zone` (`performance_id`,`zone`),
  CONSTRAINT `FKs9dau9nniwva33i1j6je1alhk` FOREIGN KEY (`performance_id`) REFERENCES `performance` (`performance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `performance_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_session` (
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `performance_id` bigint NOT NULL,
  `performance_start_at` datetime(6) NOT NULL,
  `session_num` bigint NOT NULL,
  `actor` varchar(255) NOT NULL,
  PRIMARY KEY (`performance_id`,`session_num`),
  CONSTRAINT `FKmkyy6hirggrmacpvn0jr70xdk` FOREIGN KEY (`performance_id`) REFERENCES `performance` (`performance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point` (
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `total_point` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `point_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_log` (
  `amount` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `modified_at` datetime(6) NOT NULL,
  `ref_log_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `event_id` varchar(255) NOT NULL,
  `point_type` enum('CANCELLED','EARN','PARTIAL_CANCELLED','USE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa5uf975gossx4qcco2ovxkgit` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `seat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seat` (
  `created_at` datetime(6) NOT NULL,
  `hall_id` bigint DEFAULT NULL,
  `modified_at` datetime(6) NOT NULL,
  `seat_id` bigint NOT NULL AUTO_INCREMENT,
  `seat_num` varchar(255) DEFAULT NULL,
  `seat_row` varchar(255) DEFAULT NULL,
  `zone` varchar(255) NOT NULL,
  PRIMARY KEY (`seat_id`),
  UNIQUE KEY `uk_seat` (`hall_id`,`zone`,`seat_row`,`seat_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlement_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_details` (
  `settlement_detail_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `gross_amount` bigint NOT NULL,
  `performance_id` bigint NOT NULL,
  `pg_fee_amount` bigint NOT NULL,
  `service_fee_amount` bigint NOT NULL,
  `session_num` bigint NOT NULL,
  `settlement_amount` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  PRIMARY KEY (`settlement_detail_id`),
  UNIQUE KEY `uk_settlement_detail_session` (`performance_id`,`session_num`),
  KEY `FKp4mgppkq69u31oicxnkyijkag` (`settlement_id`),
  CONSTRAINT `FKp4mgppkq69u31oicxnkyijkag` FOREIGN KEY (`settlement_id`) REFERENCES `settlements` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlement_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_policy` (
  `policy_id` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlements` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `gross_amount` bigint NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `scheduled_settlement_date` date NOT NULL,
  `seller_id` bigint NOT NULL,
  `service_fee_amount` bigint NOT NULL,
  `settlement_amount` bigint NOT NULL,
  `settlement_month` date NOT NULL,
  `settlement_status` enum('FAILED','PAID','PENDING') NOT NULL,
  `pg_fee_amount` bigint NOT NULL,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_settlement_seller_period` (`seller_id`,`settlement_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `standby`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `standby` (
  `created_at` datetime(6) NOT NULL,
  `expired_at` datetime(6) DEFAULT NULL,
  `modified_at` datetime(6) NOT NULL,
  `performance_id` bigint DEFAULT NULL,
  `priority` bigint DEFAULT NULL,
  `reserved_at` datetime(6) DEFAULT NULL,
  `session_num` bigint DEFAULT NULL,
  `standby_id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `zone1` varchar(255) DEFAULT NULL,
  `zone2` varchar(255) DEFAULT NULL,
  `zone3` varchar(255) DEFAULT NULL,
  `slot` enum('ZONE1','ZONE2','ZONE3') DEFAULT NULL,
  `standby_status` enum('CANCELLED','HELD','RESERVED','WAITING') NOT NULL,
  `notification_status` enum('PENDING','SENT','GAVE_UP') NOT NULL DEFAULT 'PENDING',
  `notification_attempts` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`standby_id`),
  UNIQUE KEY `uk_standby_user_session` (`user_id`,`session_num`,`performance_id`),
  KEY `FKd8rc8pbq6vht7m11tyb0q30tb` (`session_num`,`performance_id`),
  -- findMatchCandidate()의 PESSIMISTIC_WRITE 조회(세션+상태별 reservedAt 오름차순 1건)용.
  -- 없으면 그 세션의 모든 상태(CANCELLED/HELD/RESERVED 포함)를 다 훑으면서 락을 걺 - 대기열/이력이
  -- 쌓일수록 매칭 처리 시간이 선형으로 늘어남(실측: 노이즈 54,000행 기준 평균 1.29s → 0.085s, 약 15배).
  KEY `idx_standby_match` (`performance_id`,`session_num`,`standby_status`,`reserved_at`),
  -- NotificationReconciliationScheduler의 재시도 대상 조회(HELD + PENDING + modified_at 오름차순)용.
  KEY `idx_standby_notification_retry` (`standby_status`,`notification_status`,`modified_at`),
  CONSTRAINT `FKd8rc8pbq6vht7m11tyb0q30tb` FOREIGN KEY (`session_num`, `performance_id`) REFERENCES `performance_session` (`performance_id`, `session_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket` (
  `buy_user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `hold_expired_at` datetime(6) DEFAULT NULL,
  `modified_at` datetime(6) NOT NULL,
  `performance_id` bigint NOT NULL,
  `price` bigint NOT NULL,
  `session_num` bigint NOT NULL,
  `standby_expired_at` datetime(6) DEFAULT NULL,
  `standby_user_id` bigint DEFAULT NULL,
  `ticket_id` bigint NOT NULL AUTO_INCREMENT,
  `hold_key` varchar(255) DEFAULT NULL,
  `seat_num` varchar(255) NOT NULL,
  `seat_row` varchar(255) NOT NULL,
  `zone` varchar(255) NOT NULL,
  `ticket_status` enum('AVAILABLE','CANCELED','HOLD','RESERVED') NOT NULL DEFAULT 'AVAILABLE',
  PRIMARY KEY (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ticket_cancel_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_cancel_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `attempt_count` int NOT NULL,
  `done_at` datetime(6) DEFAULT NULL,
  `last_error` varchar(255) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `status` enum('DONE','PENDING') NOT NULL,
  `ticket_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_cancel_job_order_id` (`order_id`),
  KEY `idx_ticket_cancel_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `venue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `venue` (
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `venue_id` bigint NOT NULL AUTO_INCREMENT,
  `detail_address` varchar(255) NOT NULL,
  `notice` varchar(255) NOT NULL,
  `road_address` varchar(255) NOT NULL,
  `venue_name` varchar(255) NOT NULL,
  PRIMARY KEY (`venue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
