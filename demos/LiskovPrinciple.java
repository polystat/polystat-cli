class Parent {
    public int f(int x) {
        return x;
    }
    public int g(int x) {
        return this.f(x);
    }
}
class Child extends Parent {
    @Override
    public int f(int y) {
        return 10/y;
    }
}
public class Test {
    public static void main(String[] args) {
        Parent childInstance = new Child();
        childInstance.f(10);
    }
}
