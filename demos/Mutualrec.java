class Base {
    private int x = 0;
    public int getX() { return x; }
    public void n(int v) {
        x = v;
    }
    public void o(int v) {
        this.n(v);
    }
    public void m(int v) { 
        this.o(v); 
    }
}
class Derived extends Base {
    public void n(int v) {
        this.m(v);
    }
    public void l(int v) {
        this.n(v);
    }
}
public class Test {
    public static void main(String[] args) {
        Derived derivedInstance = new Derived();
        derivedInstance.l(10);
    }
}