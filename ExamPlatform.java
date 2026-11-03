import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Online Examination Platform (Java Swing + JDBC + MySQL)
 * Teacher: create exams, add MCQs, view students and their marks.
 * Student: register, log in, take assigned exams, see own marks.
 *
 * Default teacher login (auto-created on first run): admin / admin123
 */
public class ExamPlatform {

    // ====================== DATABASE SETTINGS (change these) ======================
    static final String URL  = "jdbc:mysql://localhost:3306/exam_db";
    static final String USER = "root";
    static final String PASS = "";          // put your MySQL password here
    // ==============================================================================

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(s.getBytes("UTF-8"))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Runs INSERT / UPDATE / DELETE. */
    static int exec(String sql, Object... params) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    /** Runs a SELECT and returns a read-only table model. */
    static DefaultTableModel query(String sql, Object... params) throws SQLException {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            String[] cols = new String[n];
            for (int i = 0; i < n; i++) cols[i] = md.getColumnLabel(i + 1);
            DefaultTableModel m = new DefaultTableModel(cols, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            while (rs.next()) {
                Object[] row = new Object[n];
                for (int i = 0; i < n; i++) row[i] = rs.getObject(i + 1);
                m.addRow(row);
            }
            return m;
        }
    }

    static void ensureDefaultTeacher() throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role='TEACHER'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) == 0) {
                exec("INSERT INTO users(username,password,role,full_name,email) VALUES(?,?,?,?,?)",
                        "admin", hash("admin123"), "TEACHER", "Default Teacher", "teacher@college.edu");
            }
        }
    }

    // ============================== Data holders ==============================
    static class ExamItem {
        int id; String title; int duration;
        ExamItem(int id, String title, int duration) { this.id = id; this.title = title; this.duration = duration; }
        @Override public String toString() { return title + " (" + duration + " min)"; }
    }

    static class Question {
        int id; String text; String[] opts = new String[4]; int correct; // 0..3
    }

    static List<ExamItem> loadExams() throws SQLException {
        List<ExamItem> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id,title,duration_min FROM exams ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new ExamItem(rs.getInt(1), rs.getString(2), rs.getInt(3)));
        }
        return list;
    }

    static void error(Component parent, Exception e) {
        JOptionPane.showMessageDialog(parent, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    static JPanel form(String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        for (int i = 0; i < labels.length; i++) { p.add(new JLabel(labels[i])); p.add(fields[i]); }
        return p;
    }

    // ================================ LOGIN ================================
    static class LoginFrame extends JFrame {
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();

        LoginFrame() {
            super("Online Examination Platform - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            JLabel title = new JLabel("Online Examination Platform", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 20));
            JButton login = new JButton("Login");
            JButton register = new JButton("Student Registration");
            JPanel btns = new JPanel(new GridLayout(1, 2, 8, 8));
            btns.add(login); btns.add(register);

            JPanel center = new JPanel(new BorderLayout(10, 10));
            center.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
            center.add(title, BorderLayout.NORTH);
            center.add(form(new String[]{"Username:", "Password:"}, new JComponent[]{user, pass}), BorderLayout.CENTER);
            center.add(btns, BorderLayout.SOUTH);
            add(center);

            login.addActionListener(e -> doLogin());
            register.addActionListener(e -> doRegister());
            getRootPane().setDefaultButton(login);
            setSize(420, 240);
            setLocationRelativeTo(null);
        }

        void doLogin() {
            String u = user.getText().trim();
            String p = new String(pass.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter username and password."); return;
            }
            try {
                DefaultTableModel m = query(
                        "SELECT id, role, full_name FROM users WHERE username=? AND password=?", u, hash(p));
                if (m.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login failed",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int id = ((Number) m.getValueAt(0, 0)).intValue();
                String role = m.getValueAt(0, 1).toString();
                String name = m.getValueAt(0, 2).toString();
                dispose();
                if (role.equals("TEACHER")) new TeacherFrame(name).setVisible(true);
                else new StudentFrame(id, name).setVisible(true);
            } catch (SQLException ex) { error(this, ex); }
        }

        void doRegister() {
            JTextField name = new JTextField(), email = new JTextField(), uname = new JTextField();
            JPasswordField pw = new JPasswordField();
            int ok = JOptionPane.showConfirmDialog(this,
                    form(new String[]{"Full name:", "Email:", "Username / Roll no:", "Password:"},
                            new JComponent[]{name, email, uname, pw}),
                    "Student Registration", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
            if (name.getText().trim().isEmpty() || uname.getText().trim().isEmpty() || pw.getPassword().length == 0) {
                JOptionPane.showMessageDialog(this, "Name, username and password are required."); return;
            }
            try {
                exec("INSERT INTO users(username,password,role,full_name,email) VALUES(?,?,?,?,?)",
                        uname.getText().trim(), hash(new String(pw.getPassword())), "STUDENT",
                        name.getText().trim(), email.getText().trim());
                JOptionPane.showMessageDialog(this, "Registered! You can log in now.");
            } catch (SQLIntegrityConstraintViolationException dup) {
                JOptionPane.showMessageDialog(this, "That username is already taken.");
            } catch (SQLException ex) { error(this, ex); }
        }
    }

    // ============================== TEACHER ==============================
    static class TeacherFrame extends JFrame {
        JTable examTable = new JTable(), resultTable = new JTable(), studentTable = new JTable();
        JComboBox<ExamItem> qExamBox = new JComboBox<>();

        TeacherFrame(String name) {
            super("Teacher Dashboard - " + name);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Exams", examsTab());
            tabs.addTab("Add MCQ", questionTab());
            tabs.addTab("Student Marks", tableTab(resultTable, this::refreshResults));
            tabs.addTab("Student Details", tableTab(studentTable, this::refreshStudents));

            JButton logout = new JButton("Logout");
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            top.add(logout);
            add(top, BorderLayout.NORTH);
            add(tabs);

            refreshAll();
            setSize(900, 560);
            setLocationRelativeTo(null);
        }

        JPanel examsTab() {
            JTextField title = new JTextField();
            JSpinner dur = new JSpinner(new SpinnerNumberModel(10, 1, 300, 1));
            JButton create = new JButton("Create Exam");
            JButton delete = new JButton("Delete Selected Exam");

            JPanel f = form(new String[]{"Exam title:", "Duration (minutes):"}, new JComponent[]{title, dur});
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
            btns.add(create); btns.add(delete);
            JPanel north = new JPanel(new BorderLayout(5, 5));
            north.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            north.add(f, BorderLayout.CENTER);
            north.add(btns, BorderLayout.SOUTH);

            JPanel p = new JPanel(new BorderLayout());
            p.add(north, BorderLayout.NORTH);
            p.add(new JScrollPane(examTable), BorderLayout.CENTER);

            create.addActionListener(e -> {
                if (title.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a title."); return; }
                try {
                    exec("INSERT INTO exams(title,duration_min) VALUES(?,?)", title.getText().trim(), dur.getValue());
                    title.setText(""); refreshAll();
                } catch (SQLException ex) { error(this, ex); }
            });
            delete.addActionListener(e -> {
                int r = examTable.getSelectedRow();
                if (r < 0) { JOptionPane.showMessageDialog(this, "Select an exam in the table."); return; }
                int id = ((Number) examTable.getValueAt(r, 0)).intValue();
                if (JOptionPane.showConfirmDialog(this, "Delete this exam with its questions and results?",
                        "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                try { exec("DELETE FROM exams WHERE id=?", id); refreshAll(); }
                catch (SQLException ex) { error(this, ex); }
            });
            return p;
        }

        JPanel questionTab() {
            JTextArea q = new JTextArea(3, 20);
            q.setLineWrap(true); q.setWrapStyleWord(true);
            JTextField a = new JTextField(), b = new JTextField(), c = new JTextField(), d = new JTextField();
            JComboBox<String> correct = new JComboBox<>(new String[]{"A", "B", "C", "D"});
            JButton add = new JButton("Add Question");

            JPanel f = form(new String[]{"Exam:", "Question:", "Option A:", "Option B:", "Option C:", "Option D:", "Correct answer:"},
                    new JComponent[]{qExamBox, new JScrollPane(q), a, b, c, d, correct});
            JPanel p = new JPanel(new BorderLayout(10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            p.add(f, BorderLayout.CENTER);
            p.add(add, BorderLayout.SOUTH);

            add.addActionListener(e -> {
                ExamItem ex = (ExamItem) qExamBox.getSelectedItem();
                if (ex == null) { JOptionPane.showMessageDialog(this, "Create an exam first."); return; }
                if (q.getText().trim().isEmpty() || a.getText().trim().isEmpty() || b.getText().trim().isEmpty()
                        || c.getText().trim().isEmpty() || d.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Fill the question and all four options."); return;
                }
                try {
                    exec("INSERT INTO questions(exam_id,question,opt_a,opt_b,opt_c,opt_d,correct) VALUES(?,?,?,?,?,?,?)",
                            ex.id, q.getText().trim(), a.getText().trim(), b.getText().trim(),
                            c.getText().trim(), d.getText().trim(), correct.getSelectedItem());
                    q.setText(""); a.setText(""); b.setText(""); c.setText(""); d.setText("");
                    refreshAll();
                    JOptionPane.showMessageDialog(this, "Question added.");
                } catch (SQLException e2) { error(this, e2); }
            });
            return p;
        }

        JPanel tableTab(JTable t, Runnable refresh) {
            JButton r = new JButton("Refresh");
            r.addActionListener(e -> refresh.run());
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(r);
            JPanel p = new JPanel(new BorderLayout());
            p.add(top, BorderLayout.NORTH);
            p.add(new JScrollPane(t), BorderLayout.CENTER);
            return p;
        }

        void refreshAll() { refreshExams(); refreshResults(); refreshStudents(); }

        void refreshExams() {
            try {
                examTable.setModel(query(
                        "SELECT e.id AS ID, e.title AS Title, e.duration_min AS Minutes, COUNT(q.id) AS Questions " +
                        "FROM exams e LEFT JOIN questions q ON q.exam_id=e.id " +
                        "GROUP BY e.id, e.title, e.duration_min ORDER BY e.id DESC"));
                qExamBox.removeAllItems();
                for (ExamItem it : loadExams()) qExamBox.addItem(it);
            } catch (SQLException ex) { error(this, ex); }
        }

        void refreshResults() {
            try {
                resultTable.setModel(query(
                        "SELECT u.full_name AS Student, u.username AS Username, u.email AS Email, e.title AS Exam, " +
                        "r.score AS Score, r.total AS Total, ROUND(r.score*100/r.total,1) AS Percent, r.taken_on AS Date " +
                        "FROM results r JOIN users u ON r.student_id=u.id JOIN exams e ON r.exam_id=e.id " +
                        "ORDER BY r.taken_on DESC"));
            } catch (SQLException ex) { error(this, ex); }
        }

        void refreshStudents() {
            try {
                studentTable.setModel(query(
                        "SELECT id AS ID, full_name AS Name, username AS Username, email AS Email " +
                        "FROM users WHERE role='STUDENT' ORDER BY full_name"));
            } catch (SQLException ex) { error(this, ex); }
        }
    }

    // ============================== STUDENT ==============================
    static class StudentFrame extends JFrame {
        final int studentId;
        JComboBox<ExamItem> examBox = new JComboBox<>();
        JTable myResults = new JTable();

        StudentFrame(int id, String name) {
            super("Student Dashboard - " + name);
            this.studentId = id;
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JButton start = new JButton("Start Exam");
            JButton logout = new JButton("Logout");
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            top.add(new JLabel("Available exams:"));
            top.add(examBox); top.add(start); top.add(logout);

            JPanel p = new JPanel(new BorderLayout());
            p.add(top, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(myResults);
            sp.setBorder(BorderFactory.createTitledBorder("My results"));
            p.add(sp, BorderLayout.CENTER);
            add(p);

            start.addActionListener(e -> startExam());
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            refresh();
            setSize(760, 400);
            setLocationRelativeTo(null);
        }

        void refresh() {
            try {
                examBox.removeAllItems();
                for (ExamItem it : loadExams()) examBox.addItem(it);
                myResults.setModel(query(
                        "SELECT e.title AS Exam, r.score AS Score, r.total AS Total, " +
                        "ROUND(r.score*100/r.total,1) AS Percent, r.taken_on AS Date " +
                        "FROM results r JOIN exams e ON r.exam_id=e.id WHERE r.student_id=? ORDER BY r.taken_on DESC",
                        studentId));
            } catch (SQLException ex) { error(this, ex); }
        }

        void startExam() {
            ExamItem ex = (ExamItem) examBox.getSelectedItem();
            if (ex == null) { JOptionPane.showMessageDialog(this, "No exam available."); return; }
            try {
                if (query("SELECT id FROM results WHERE student_id=? AND exam_id=?", studentId, ex.id).getRowCount() > 0) {
                    JOptionPane.showMessageDialog(this, "You have already attempted this exam."); return;
                }
                List<Question> qs = new ArrayList<>();
                DefaultTableModel m = query(
                        "SELECT id,question,opt_a,opt_b,opt_c,opt_d,correct FROM questions WHERE exam_id=?", ex.id);
                for (int i = 0; i < m.getRowCount(); i++) {
                    Question q = new Question();
                    q.id = ((Number) m.getValueAt(i, 0)).intValue();
                    q.text = m.getValueAt(i, 1).toString();
                    for (int k = 0; k < 4; k++) q.opts[k] = m.getValueAt(i, 2 + k).toString();
                    q.correct = "ABCD".indexOf(m.getValueAt(i, 6).toString().charAt(0));
                    qs.add(q);
                }
                if (qs.isEmpty()) { JOptionPane.showMessageDialog(this, "This exam has no questions yet."); return; }
                Collections.shuffle(qs);
                if (JOptionPane.showConfirmDialog(this,
                        "Start \"" + ex.title + "\"?\n" + qs.size() + " questions, " + ex.duration +
                        " minutes. You can attempt it only once.", "Start exam",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                new ExamFrame(this, studentId, ex, qs).setVisible(true);
            } catch (SQLException e) { error(this, e); }
        }
    }

    // ============================== EXAM SCREEN ==============================
    static class ExamFrame extends JFrame {
        final StudentFrame parent;
        final int studentId;
        final ExamItem exam;
        final List<Question> qs;
        final int[] answers;
        int idx = 0, secondsLeft;
        boolean finished = false;

        JLabel lblProgress = new JLabel(), lblTimer = new JLabel();
        JTextArea qText = new JTextArea(4, 40);
        JRadioButton[] rb = new JRadioButton[4];
        ButtonGroup bg = new ButtonGroup();
        JButton prev = new JButton("Previous"), next = new JButton("Next"), submit = new JButton("Submit Exam");
        javax.swing.Timer timer;

        ExamFrame(StudentFrame parent, int studentId, ExamItem exam, List<Question> qs) {
            super("Exam: " + exam.title);
            this.parent = parent; this.studentId = studentId; this.exam = exam; this.qs = qs;
            this.answers = new int[qs.size()];
            java.util.Arrays.fill(answers, -1);
            this.secondsLeft = exam.duration * 60;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

            lblTimer.setFont(new Font("SansSerif", Font.BOLD, 16));
            lblProgress.setFont(new Font("SansSerif", Font.BOLD, 14));
            JPanel top = new JPanel(new BorderLayout());
            top.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
            top.add(lblProgress, BorderLayout.WEST);
            top.add(lblTimer, BorderLayout.EAST);

            qText.setEditable(false); qText.setLineWrap(true); qText.setWrapStyleWord(true);
            qText.setFont(new Font("SansSerif", Font.PLAIN, 16));
            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
            center.add(new JScrollPane(qText));
            center.add(Box.createVerticalStrut(15));
            for (int i = 0; i < 4; i++) {
                rb[i] = new JRadioButton();
                rb[i].setFont(new Font("SansSerif", Font.PLAIN, 15));
                bg.add(rb[i]); center.add(rb[i]);
            }

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
            bottom.add(prev); bottom.add(next); bottom.add(submit);
            add(top, BorderLayout.NORTH); add(center, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);

            prev.addActionListener(e -> go(-1));
            next.addActionListener(e -> go(1));
            submit.addActionListener(e -> {
                saveCurrent();
                int unanswered = 0;
                for (int a : answers) if (a < 0) unanswered++;
                String msg = unanswered > 0 ? unanswered + " question(s) unanswered. Submit anyway?" : "Submit your exam?";
                if (JOptionPane.showConfirmDialog(this, msg, "Submit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                    finish(false);
            });
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    JOptionPane.showMessageDialog(ExamFrame.this, "Please use 'Submit Exam' to finish.");
                }
            });

            timer = new javax.swing.Timer(1000, e -> {
                secondsLeft--;
                updateTimer();
                if (secondsLeft <= 0) { saveCurrent(); finish(true); }
            });
            timer.start();

            showQuestion();
            updateTimer();
            setSize(760, 480);
            setLocationRelativeTo(null);
        }

        void updateTimer() {
            lblTimer.setText(String.format("Time left: %02d:%02d", secondsLeft / 60, Math.max(secondsLeft, 0) % 60));
        }

        void saveCurrent() {
            for (int i = 0; i < 4; i++) if (rb[i].isSelected()) answers[idx] = i;
        }

        void go(int step) {
            saveCurrent();
            idx += step;
            showQuestion();
        }

        void showQuestion() {
            bg.clearSelection();
            Question q = qs.get(idx);
            lblProgress.setText("Question " + (idx + 1) + " of " + qs.size());
            qText.setText(q.text);
            for (int i = 0; i < 4; i++) rb[i].setText((char) ('A' + i) + ".  " + q.opts[i]);
            if (answers[idx] >= 0) rb[answers[idx]].setSelected(true);
            prev.setEnabled(idx > 0);
            next.setEnabled(idx < qs.size() - 1);
        }

        void finish(boolean timeUp) {
            if (finished) return;
            finished = true;
            timer.stop();
            int score = 0;
            for (int i = 0; i < qs.size(); i++) if (answers[i] == qs.get(i).correct) score++;
            try {
                exec("INSERT INTO results(student_id,exam_id,score,total) VALUES(?,?,?,?)",
                        studentId, exam.id, score, qs.size());
            } catch (SQLException ex) { error(this, ex); }
            JOptionPane.showMessageDialog(this,
                    (timeUp ? "Time is up! Exam auto-submitted.\n" : "Exam submitted.\n") +
                    "Your score: " + score + " / " + qs.size(), "Result", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            parent.refresh();
        }
    }

    // ================================ MAIN ================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                ensureDefaultTeacher();
                new LoginFrame().setVisible(true);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null,
                        "Cannot connect to the database.\nCheck that MySQL is running, database.sql was executed,\n" +
                        "the MySQL connector JAR is added in NetBeans, and URL/USER/PASS are correct.\n\n" + e.getMessage(),
                        "Database error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
