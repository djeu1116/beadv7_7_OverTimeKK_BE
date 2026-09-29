-- ============================================================
-- DB 물리 분리 2단계 - payment-service 전용 MySQL 인스턴스 초기화 스크립트.
-- reseat_payment 스키마 + payment_service 전용 계정.
-- reconciliation_task는 order-service DB(init-order.sql)에도 동일 스키마로 복제됨
-- (payment-service는 나머지 8종 타입을 씀).
-- 상세: Obsidian programmers/payment_order_split/db_separation_plan.md
-- ============================================================


-- payment-service 소유. reconciliation_task는 order-service DB에도 동일 스키마로 복제됨(나머지 8종 타입을 씀)
CREATE DATABASE IF NOT EXISTS reseat_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER DATABASE reseat_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
USE reseat_payment;

DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `amount` bigint NOT NULL,
  `attempt_seq` int NOT NULL DEFAULT '0',
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
DROP TABLE IF EXISTS `payment_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
-- 로드맵 3단계(PaymentAttempt+포인트 원장)에서 빠졌던 테이블 - DB 분리 2단계에서 진짜 빈 볼륨으로
-- 처음 ddl-auto=validate를 태우다 발견됨(기존 볼륨은 수동으로 만들어둔 테이블이 남아있어서 못 잡았음).
CREATE TABLE `payment_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `payment_id` bigint NOT NULL,
  `attempt_seq` int NOT NULL,
  `payment_key` varchar(255) DEFAULT NULL,
  `pg_order_id` varchar(255) DEFAULT NULL,
  `used_point` bigint NOT NULL,
  `status` enum('READY','PENDING_VERIFICATION','PAID','FAILED') NOT NULL,
  PRIMARY KEY (`id`)
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
DROP TABLE IF EXISTS `point_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `use_log_id` bigint NOT NULL,
  `used_amount` bigint NOT NULL,
  `remaining_refundable` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_ledger_use_log_id` (`use_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `outbox_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(50) NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `payload` json NOT NULL,
  `status` enum('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime(6) NOT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_outbox_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `reconciliation_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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

-- DB 물리 분리 1단계: payment-service 전용 계정, 자기 스키마만 접근 가능.
-- 비밀번호는 로컬/개발용 기본값 — 실제 배포 전에는 반드시 교체.
CREATE USER IF NOT EXISTS 'payment_service'@'%' IDENTIFIED BY 'payment-service-dev-pw-change-me';
GRANT ALL PRIVILEGES ON reseat_payment.* TO 'payment_service'@'%';
FLUSH PRIVILEGES;


