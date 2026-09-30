-- ============================================================
-- DB 물리 분리 4단계 - performance-service 전용 MySQL 인스턴스 초기화 스크립트.
-- reseat_performance 스키마 + performance_service 전용 계정.
-- reconciliation_task는 다른 3개 서비스 DB에도 동일 스키마로 복제됨 - common 모듈의
-- ReconciliationTask 엔티티가 전 서비스에 스캔되기 때문(order/payment 상세는 init-order.sql 참고).
-- 상세: Obsidian programmers/payment_order_split/db_separation_plan.md
-- ============================================================


-- performance-service 소유
CREATE DATABASE IF NOT EXISTS reseat_performance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER DATABASE reseat_performance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET NAMES utf8mb4;

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
USE reseat_performance;

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
DROP TABLE IF EXISTS `reconciliation_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
-- common 모듈의 ReconciliationTask 엔티티가 모든 서비스에 컴포넌트/엔티티 스캔되어(각 서비스
-- @SpringBootApplication의 베이스 패키지가 com.programmers.kdt로 common과 겹침) ddl-auto=validate가
-- 이 테이블 존재를 요구한다 - performance-service 도메인 전용 ReconciliationTaskType은 아직 없어서
-- 항상 빈 테이블이지만, 구조적으로 필요하다(order/payment의 공유 테이블 복제와 같은 이유).
CREATE TABLE `reconciliation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(50) NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `amount` bigint DEFAULT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `status` enum('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
  `resolved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reconciliation_task_status_type` (`status`,`task_type`)
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

-- DB 물리 분리 3단계에서 만든 계정을 그대로 재사용(4단계는 인스턴스만 승격, 계정/비번은 안 바뀜).
CREATE USER IF NOT EXISTS 'performance_service'@'%' IDENTIFIED BY 'performance-service-dev-pw-change-me';
GRANT ALL PRIVILEGES ON reseat_performance.* TO 'performance_service'@'%';
FLUSH PRIVILEGES;
