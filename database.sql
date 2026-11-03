-- Run this once in MySQL (Workbench / phpMyAdmin / XAMPP / command line)
CREATE DATABASE IF NOT EXISTS exam_db;
USE exam_db;

CREATE TABLE IF NOT EXISTS users (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    username  VARCHAR(50)  NOT NULL UNIQUE,
    password  VARCHAR(64)  NOT NULL,              -- SHA-256 hash
    role      ENUM('TEACHER','STUDENT') NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email     VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS exams (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(150) NOT NULL,
    duration_min INT NOT NULL DEFAULT 10
);

CREATE TABLE IF NOT EXISTS questions (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    exam_id  INT NOT NULL,
    question TEXT NOT NULL,
    opt_a    VARCHAR(255) NOT NULL,
    opt_b    VARCHAR(255) NOT NULL,
    opt_c    VARCHAR(255) NOT NULL,
    opt_d    VARCHAR(255) NOT NULL,
    correct  CHAR(1) NOT NULL,                    -- A, B, C or D
    FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS results (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    exam_id    INT NOT NULL,
    score      INT NOT NULL,
    total      INT NOT NULL,
    taken_on   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, exam_id),                 -- one attempt per exam
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (exam_id)    REFERENCES exams(id) ON DELETE CASCADE
);
