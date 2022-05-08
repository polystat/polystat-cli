class MutualRec {
    class Base {
        void f(int x) {
        }

        void g(int x) {
            f(x);
        }
    }

    class Derived extends Base {
        @Override
        void f(int x) {
            g(x);
        }
    }
}
