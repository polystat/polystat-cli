class c:
    def f(self, x): 
        return x
    def g(self, x):
        return self.f(x)
class d(c):
    def f(self, x):
        return self.g(x)
