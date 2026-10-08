/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
public class Student extends Person {
    private Course course;
    public Student(String name, Course course) { super(name); this.course = course; }
    public Course getCourse() { return course; }
}
