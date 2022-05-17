class Base:
    def f(self, v):
        self.g(v)
    def g(self, v):
        v += 1
        print(v)

class Derived(Base):
    def g(self, v):
        self.f(v)

if __name__ == "__main__":
    print(Derived().g(12))
