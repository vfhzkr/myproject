-- 创建数据库
CREATE DATABASE IF NOT EXISTS ticket_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ticket_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    phone VARCHAR(20) DEFAULT '' COMMENT '手机号',
    email VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 车次表
CREATE TABLE IF NOT EXISTS train (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    train_no VARCHAR(20) NOT NULL COMMENT '车次号',
    departure VARCHAR(50) NOT NULL COMMENT '出发站',
    destination VARCHAR(50) NOT NULL COMMENT '到达站',
    departure_time DATETIME NOT NULL COMMENT '出发时间',
    arrival_time DATETIME NOT NULL COMMENT '到达时间',
    total_seats INT NOT NULL COMMENT '总座位数',
    available_seats INT NOT NULL COMMENT '剩余座位',
    price DECIMAL(10,2) NOT NULL COMMENT '票价',
    status TINYINT DEFAULT 1 COMMENT '1=可售 0=停售',
    INDEX idx_departure_dest (departure, destination),
    INDEX idx_departure_time (departure_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    train_id BIGINT NOT NULL COMMENT '车次ID',
    seat_count INT NOT NULL COMMENT '购票数量',
    total_price DECIMAL(10,2) NOT NULL COMMENT '总价',
    status TINYINT DEFAULT 1 COMMENT '1=已购 2=已退票',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_train_id (train_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 候补表
CREATE TABLE IF NOT EXISTS waitlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    train_id BIGINT NOT NULL COMMENT '车次ID',
    seat_count INT NOT NULL COMMENT '候补数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 0 COMMENT '0=等待 1=已转正 2=已取消',
    INDEX idx_train_status (train_id, status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入测试数据
INSERT INTO train (train_no, departure, destination, departure_time, arrival_time, total_seats, available_seats, price, status) VALUES
('G101', '北京', '上海', '2026-06-01 08:00:00', '2026-06-01 12:30:00', 500, 500, 553.00, 1),
('G102', '上海', '北京', '2026-06-01 09:00:00', '2026-06-01 13:30:00', 500, 500, 553.00, 1),
('G201', '北京', '广州', '2026-06-01 10:00:00', '2026-06-01 18:00:00', 400, 400, 862.00, 1),
('G202', '广州', '北京', '2026-06-01 11:00:00', '2026-06-01 19:00:00', 400, 400, 862.00, 1),
('G301', '深圳', '武汉', '2026-06-01 08:30:00', '2026-06-01 13:00:00', 300, 300, 538.00, 1),
('G302', '武汉', '深圳', '2026-06-01 14:00:00', '2026-06-01 18:30:00', 300, 300, 538.00, 1),
('G401', '成都', '重庆', '2026-06-01 07:00:00', '2026-06-01 08:30:00', 200, 200, 154.00, 1),
('G402', '重庆', '成都', '2026-06-01 09:00:00', '2026-06-01 10:30:00', 200, 200, 154.00, 1),
('G501', '北京', '天津', '2026-06-01 06:00:00', '2026-06-01 06:30:00', 100, 100, 54.50, 1),
('G502', '天津', '北京', '2026-06-01 07:00:00', '2026-06-01 07:30:00', 100, 100, 54.50, 1);
