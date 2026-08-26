package dev.other.vector;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Vec4i {
    public int x, y, z, w;

    public static Vec4i copy(int value) {
        return new Vec4i(value, value, value, value);
    }

    public Vec4i copy() {
        return new Vec4i(this.x, this.y, this.z, this.w);
    }

    public int get(int component) throws IllegalArgumentException {
        return switch (component) {
            case 0 -> this.x;
            case 1 -> this.y;
            case 2 -> this.z;
            case 3 -> this.w;
            default -> throw new IllegalArgumentException();
        };
    }
}
