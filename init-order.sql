-- ============================================================
-- DB 물리 분리 2단계 - order-service 전용 MySQL 인스턴스 초기화 스크립트.
-- reseat_order 스키마 + order_service 전용 계정.
-- reconciliation_task는 payment-service DB(init-payment.sql)에도 동일 스키마로 복제됨
-- (order-service는 ORDER_OUTBOX_DELIVERY_FAILED 타입만 씀).
-- 상세: Obsidian programmers/payment_order_split/db_separation_plan.md
-- ============================================================


-- order-service 소유. reconciliation_task는 payment-service DB에도 동일 스키마로 복제됨(ORDER_OUTBOX_DELIVERY_FAILED만 씀)
CREATE DATABASE IF NOT EXISTS reseat_order CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER DATABASE reseat_order CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
USE reseat_order;

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
DROP TABLE IF EXISTS `order_outbox_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_outbox_event` (
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
  KEY `idx_order_outbox_status_retry` (`status`,`next_retry_at`)
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

-- DB 물리 분리 1단계: order-service 전용 계정, 자기 스키마만 접근 가능.
-- 비밀번호는 로컬/개발용 기본값 — 실제 배포 전에는 반드시 교체(현재 INTERNAL_AUTH_TOKEN GitHub 시크릿
-- 미등록과 같은 종류의 "코드 밖에서 조치 필요" 항목으로 남겨둠).
CREATE USER IF NOT EXISTS 'order_service'@'%' IDENTIFIED BY 'order-service-dev-pw-change-me';
GRANT ALL PRIVILEGES ON reseat_order.* TO 'order_service'@'%';
FLUSH PRIVILEGES;


