class Parent {
    public int f(int x) {
        int t = x - 5;
        assert(t > 0);
        return x;
    }
    public int g(int y) {
        return this.f(y);
    }
    public int gg(int y2) {
        return this.g(y2);
    }
    public int ggg(int y3) {
        return this.gg(y3);
    }
    public int h(int z) {
        return z;
    }
}
class Child extends Parent {
    @Override
    public int f(int y) {
        return y;
    }
    @Override
    public int h(int z) {
        return this.ggg(z);
    }
};
public class Test {
    public static void main(String[] args) {
        int x = 10;
        Parent p = new Parent();
        p.g(x);
        x -= 5;
        p.h(x);
        p = new Child();
        p.g(x);
        p.h(x);
    }
}