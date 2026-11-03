# Online Examination Platform

A Java Swing-based examination system integrated with MySQL that allows teachers to create exams and students to attend timed tests with automatic evaluation.

## Features

- Teacher and student login
- Student registration
- Create and delete examinations
- Add multiple-choice questions (MCQs)
- Timed examinations with countdown timer
- Random question order
- Automatic submission and evaluation
- View student marks and results
- Store examination data using MySQL

## Technologies Used

- Java (JDK 8 or later)
- Java Swing
- MySQL
- JDBC
- MySQL Connector/J
- NetBeans IDE

## Project Structure

```text
Online-Examination-Platform/
│
├── ExamPlatform.java
├── database.sql
├── Project_Report.pdf
└── README.md
```

## Installation and Setup

1. Install Java JDK 8 or later.
2. Install and start MySQL Server.
3. Open MySQL Workbench.
4. Run the `database.sql` script to create the `exam_db` database and required tables.
5. Create a Java project in NetBeans.
6. Add `ExamPlatform.java` to the project.
7. Add the MySQL Connector/J JAR to the project libraries.
8. Configure the database URL, username and password in the Java source code.
9. Run the application.

## User Roles

**Teacher:**
- Create and manage exams.
- Add MCQ questions.
- View student details and marks.

**Student:**
- Register and log in.
- View available exams.
- Attend timed examinations.
- View examination results.

## Database

The project uses a MySQL database named `exam_db` with four main tables:

- `users`
- `exams`
- `questions`
- `results`

## Future Enhancements

- Improved user interface
- Support for additional question types
- Online deployment
- Detailed performance analytics

---

**Note:** Update database credentials before running the application. Do not upload real passwords or private credentials to a public repository.
