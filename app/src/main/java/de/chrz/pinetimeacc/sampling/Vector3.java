package de.chrz.pinetimeacc.sampling;

public class Vector3 {

    public double x, y, z;

    public void add(Vector3 other) {
        x += other.x;
        y += other.y;
        z += other.z;
    }

    public void multiply(double factor) {
        x *= factor;
        y *= factor;
        z *= factor;
    }

    public double magnitude() {
        return Math.sqrt((x * x) + (y * y) + (z * z));
    }

}
