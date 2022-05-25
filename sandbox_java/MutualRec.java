class Base {
    int f(int x) {
        return x;
    }

    int g(int x) {
        return f(x);
    }
}

class Derived extends Base {
    @Override
    int f(int x) {
        return g(x);
    }
}
