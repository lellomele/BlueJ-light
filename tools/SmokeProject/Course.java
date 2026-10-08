/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
public class Course {
    private String title;
    private java.util.List<Student> students = new java.util.ArrayList<>();
    public Course(String title) { this.title = title; }
    public void enrol(Student student) { students.add(student); }
    public String getTitle() { return title; }
}
