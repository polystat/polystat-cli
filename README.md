![logo](https://camo.githubusercontent.com/249d7357b20b54fb522bb75c82902fb6ae47d894c36015aba2c7b7f23a39b65d/68747470733a2f2f7777772e706f6c79737461742e6f72672f6c6f676f2e737667)

[![Continuous Integration](https://github.com/polystat/polystat-cli/actions/workflows/ci.yml/badge.svg)](https://github.com/polystat/polystat-cli/actions/workflows/ci.yml)
[![Hits-of-Code](https://hitsofcode.com/github/polystat/polystat-cli)](https://hitsofcode.com/view/github/polystat/polystat-cli)
![Lines of code](https://img.shields.io/tokei/lines/github/polystat/polystat-cli)

[![Maven Release](https://badgen.net/maven/v/metadata-url/https/repo1.maven.org/maven2/org/polystat/polystat-cli_3/maven-metadata.xml)](https://oss.sonatype.org/content/repositories/releases/org/polystat/polystat-cli_3/)
![GitHub](https://img.shields.io/github/license/polystat/polystat-cli)
![GitHub Release Date](https://img.shields.io/github/release-date/polystat/polystat-cli)
![GitHub all releases](https://img.shields.io/github/downloads/polystat/polystat-cli/total)

# Polystat CLI
- [Polystat CLI](#polystat-cli)
- [Defects](#defects)
  - [Unanticipated mutual recursion](#unanticipated-mutual-recursion)
  - [Unjustified assumptions about methods of superclasses](#unjustified-assumptions-about-methods-of-superclasses)
  - [Direct Access to the Base Class State](#direct-access-to-the-base-class-state)
  - [Violation of the Liskov substitution principle](#violation-of-the-liskov-substitution-principle)
  - [Division by zero](#division-by-zero)
- [Installation](#installation)
  - [With coursier](#with-coursier)
  - [Using a "fat" jar](#using-a-fat-jar)
- [Basic usage](#basic)
- [Running on big projects (hadoop)](#running-on-big-projects-hadoop)
- [Full Usage Specification](#full)
  - [Notation](#notation)
  - [Input configuration](#input-configuration)
  - [Configuration options](#configuration-options)
  - [Output configuration](#output-configuration)
  - [`polystat list`](#polystat-list)
  - [Other Options](#other-options)
- [Configuration File](#configuration-file)
- [Development](#development)

This repository provides an alternative implementation to [Polystat](https://github.com/polystat/polystat). This tool's objective is to extend the functionality of the original implementation. These extensions include:
* A precise [specification](#full) for the command-line interface. 
* A configuration file that is not tied to the command-line interface.  
* A setup-free and customizable integration with the existing source-to-EO translators (specifically [j2eo](https://github.com/polystat/j2eo) and [py2eo](https://github.com/polystat/py2eo)). The following features are implemented for the `j2eo` translator:
    * Automatic downloading of the specified version from Maven Central
    * If you have `j2eo` installed locally, you can provide a path to it via a configuration option.
* The [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html) output of the analyzers can be produced in the following two forms:
    * A **directory** with the `.sarif` files, where each SARIF file corresponds to the file in the input directory. 
    * An **single file** where the outputs of the analyzers for all the analyzed files are aggregated in a single SARIF JSON object. 

...and many minor quality-of-life improvements. 

⚠ WARNING ⚠: The tool is still in the early stages of development, so feature suggestions and bug reports are more than welcome!

# Defects

This section describes the defects that the Polystat CLI can detect by analyzing the EO intermediate representation produced by the translators, such as `j2eo` and `py2eo`.

## Unanticipated mutual recursion

[(Back to TOC)](#polystat-cli)

Comes from: [polystat/odin](https://github.com/polystat/odin#mutual-recursion-analyzer)

Unanticipated mutual recursion happens when a subclass redefines some of the methods of the superclass in such a way that one of the methods of the superclass becomes mutually-recursive with one of the redefined methods.

Sample input (Java):

```java
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
```

<details>

<summary>Show translation to EO (generated with J2EO v0.5.3)</summary>
   
```python
# 2022-06-20T16:48:51.454871657
# j2eo team
+alias stdlib.lang.class__Object
+alias stdlib.primitives.prim__int
+alias org.eolang.gray.cage

[] > class__Base
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Base" > className
      [this] > init
        seq > @
          d568221876
        [] > d568221876
          this.x.write > @
            i_s1353070773
        [] > i_s1353070773
          l825658265 > @
        [] > l825658265
          prim__int.constructor_2 > @
            prim__int.new
            0
      prim__int.constructor_1 > x
        prim__int.new
      # getX :: null -> int
      [this] > getX
        seq > @
          s204715855
        [] > s204715855
          s_r1888442711 > @
        [] > s_r1888442711
          x > @
      # n :: int -> void
      [this v] > n
        seq > @
          s550402284
        [] > s550402284
          s_r1438098656.write > @
            s_r1594199808
        [] > s_r1438098656
          x > @
        [] > s_r1594199808
          v > @
      # o :: int -> void
      [this v] > o
        seq > @
          s1769190683
        [] > s1769190683
          this.n > @
            this
            s_r1201484275
        [] > s_r1201484275
          v > @
      # m :: int -> void
      [this v] > m
        seq > @
          s1089418272
        [] > s1089418272
          this.o > @
            this
            s_r1233990028
        [] > s_r1233990028
          v > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1509791656
      this
    [] > initialization
      this.init > @
        this
    [] > s1509791656
      super.constructor > @
        this.super

[] > class__Derived
  class__Base > super
  super > @
  [] > new
    [] > this
      class__Base.new > super
      super > @
      "class__Derived" > className
      [this] > init
        seq > @
          TRUE
      # n :: int -> void
      [this v] > n
        seq > @
          s257608164
        [] > s257608164
          this.m > @
            this
            s_r306115458
        [] > s_r306115458
          v > @
      # l :: int -> void
      [this v] > l
        seq > @
          s230643635
        [] > s230643635
          this.n > @
            this
            s_r944427387
        [] > s_r944427387
          v > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1636182655
      this
    [] > initialization
      this.init > @
        this
    [] > s1636182655
      super.constructor > @
        this.super

[] > class__Test
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Test" > className
      [this] > init
        seq > @
          TRUE
    seq > @
      this
  # main :: String[] -> void
  [args] > main
    seq > @
      d71399214
      s1390869998
    cage > derivedInstance
    [] > d71399214
      derivedInstance.write > @
        i_s1932831450
    [] > i_s1932831450
      inst496729294 > @
    [] > inst496729294
      class__Derived.constructor > @
        class__Derived.new
    [] > s1390869998
      derivedInstance.l > @
        derivedInstance
        l1820383114
    [] > l1820383114
      prim__int.constructor_2 > @
        prim__int.new
        10
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1645547422
      this
    [] > initialization
      this.init > @
        this
    [] > s1645547422
      super.constructor > @
        this.super

[args...] > main
  class__Test.main > @
    *
```
</details>


Analyzer output:

```
class__Derived.new: 
    class__Derived.new.m (was last redefined in "class__Base.new.this") -> 
    class__Derived.new.o (was last redefined in "class__Base.new.this") -> 
    class__Derived.new.n (was last redefined in "class__Derived.new.this") -> 
    class__Derived.new.m (was last redefined in "class__Base.new.this")

class__Derived.new.this: 
  class__Derived.new.this.m (was last redefined in "class__Base.new.this") ->
  class__Derived.new.this.o (was last redefined in "class__Base.new.this") ->
  class__Derived.new.this.n ->
  class__Derived.new.this.m (was last redefined in "class__Base.new.this")
```

## Unjustified assumptions about methods of superclasses

[(Back to TOC)](#polystat-cli)

If the superclass contains this defect, this means that the inlining of one its
the methods is not safe, because doing so may lead to breaking changes in its subclasses. 

Comes from: [polystat/odin](https://github.com/polystat/polystat-cli#unjustified-assumptions-about-methods-of-superclasses)

Sample input (Java):

```java
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
```


<details>

<summary>

Show translation to EO (generated with J2EO v0.5.3)</summary>
```
# 2022-06-20T16:48:51.476031558
# j2eo team
+alias stdlib.lang.class__Object
+alias stdlib.primitives.prim__int
+alias org.eolang.gray.cage

[] > class__Parent
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Parent" > className
      [this] > init
        seq > @
          TRUE
      # f :: int -> int
      [this x] > f
        seq > @
          d2012330741
          s1437654187
          s936292831
        prim__int.constructor_1 > t
          prim__int.new
        [] > d2012330741
          t.write > @
            i_s1101184763
        [] > i_s1101184763
          b1816147548 > @
        [] > b1816147548
          s_r2079179914.sub > @
            l20049680
        [] > s_r2079179914
          x > @
        [] > l20049680
          prim__int.constructor_2 > @
            prim__int.new
            5
        [] > s1437654187
          p951050903.if > @
            TRUE
            []
              "AssertionError" > msg
        [] > p951050903
          b770947228 > @
        [] > b770947228
          s_r590646109.greater > @
            l1882349076
        [] > s_r590646109
          t > @
        [] > l1882349076
          prim__int.constructor_2 > @
            prim__int.new
            0
        [] > s936292831
          s_r130668770 > @
        [] > s_r130668770
          x > @
      # g :: int -> int
      [this y] > g
        seq > @
          s2151717
        [] > s2151717
          m_i1644231115 > @
        [] > m_i1644231115
          this.f > @
            this
            s_r537066525
        [] > s_r537066525
          y > @
      # gg :: int -> int
      [this y2] > gg
        seq > @
          s1766145591
        [] > s1766145591
          m_i1867139015 > @
        [] > m_i1867139015
          this.g > @
            this
            s_r182531396
        [] > s_r182531396
          y2 > @
      # ggg :: int -> int
      [this y3] > ggg
        seq > @
          s1026871825
        [] > s1026871825
          m_i2109798150 > @
        [] > m_i2109798150
          this.gg > @
            this
            s_r1074389766
        [] > s_r1074389766
          y3 > @
      # h :: int -> int
      [this z] > h
        seq > @
          s1136768342
        [] > s1136768342
          s_r1484673893 > @
        [] > s_r1484673893
          z > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s587003819
      this
    [] > initialization
      this.init > @
        this
    [] > s587003819
      super.constructor > @
        this.super

[] > class__Child
  class__Parent > super
  super > @
  [] > new
    [] > this
      class__Parent.new > super
      super > @
      "class__Child" > className
      [this] > init
        seq > @
          TRUE
      # f :: int -> int
      [this y] > f
        seq > @
          s769798433
        [] > s769798433
          s_r1665620686 > @
        [] > s_r1665620686
          y > @
      # h :: int -> int
      [this z] > h
        seq > @
          s1233705144
        [] > s1233705144
          m_i202125197 > @
        [] > m_i202125197
          this.ggg > @
            this
            s_r811301908
        [] > s_r811301908
          z > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1762902523
      this
    [] > initialization
      this.init > @
        this
    [] > s1762902523
      super.constructor > @
        this.super

[] > class__Test
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Test" > className
      [this] > init
        seq > @
          TRUE
    seq > @
      this
  # main :: String[] -> void
  [args] > main
    seq > @
      d1725008249
      d402115881
      s361398902
      s2044215423
      s1313916817
      s1487500813
      s1231156911
      s1708169732
    prim__int.constructor_1 > x
      prim__int.new
    [] > d1725008249
      x.write > @
        i_s197964393
    [] > i_s197964393
      l1620890840 > @
    [] > l1620890840
      prim__int.constructor_2 > @
        prim__int.new
        10
    cage > p
    [] > d402115881
      p.write > @
        i_s2106000623
    [] > i_s2106000623
      inst330739404 > @
    [] > inst330739404
      class__Parent.constructor > @
        class__Parent.new
    [] > s361398902
      p.g > @
        p
        s_r1010670443
    [] > s_r1010670443
      x > @
    [] > s2044215423
      s_r1606304070.sub_equal > @
        l510063093
    [] > s_r1606304070
      x > @
    [] > l510063093
      prim__int.constructor_2 > @
        prim__int.new
        5
    [] > s1313916817
      p.h > @
        p
        s_r1966124444
    [] > s_r1966124444
      x > @
    [] > s1487500813
      s_r1911152052.write > @
        inst961409111
    [] > s_r1911152052
      p > @
    [] > inst961409111
      Child.constructor > @
        Child.new
    [] > s1231156911
      p.g > @
        p
        s_r1525409936
    [] > s_r1525409936
      x > @
    [] > s1708169732
      p.h > @
        p
        s_r868815265
    [] > s_r868815265
      x > @
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1977310713
      this
    [] > initialization
      this.init > @
        this
    [] > s1977310713
      super.constructor > @
        this.super

[args...] > main
  class__Test.main > @
    *
```
</details>


Analyzer output:

```
Inlining calls in method g is not safe: doing so may break the behaviour of subclasses!
Inlining calls in method ggg is not safe: doing so may break the behaviour of subclasses!
```

## Direct Access to the Base Class State

[(Back to TOC)](#polystat-cli)

This defect means that the analyzed program contains the parts where the fields of the object are accessed directly. This probably means that the object with such fields breaks the incapsulation by exposing some of its private fields. 

Comes from: [polystat/odin](https://github.com/polystat/odin#direct-access-to-the-base-class-state-analyzer)

__WARNING__: With the current latest version of `j2eo` (v0.5.3), the direct state access defect is not detected. It should work when [j2eo#114](https://github.com/polystat/j2eo/issues/114) is fixed.

__UPDATE__: Odin v0.4.5 introduced a workaround that made the Direct State Access defect detectable in some cases. 

Sample input (Java):

```java
class A {
    protected int state = 0;
};

class B extends A {
    public int n(int x) {
        return this.state + x;
    }
}
```
<details>
<summary>

Show translation to EO (generated with J2EO v0.5.3)</summary>
```python
# 2022-07-01T15:16:39.276365661
# j2eo team
+alias stdlib.lang.class__Object
+alias stdlib.primitives.prim__int

[] > class__A
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__A" > className
      [this] > init
        seq > @
          d580718781
        [] > d580718781
          this.state.write > @
            i_s1840976765
        [] > i_s1840976765
          l436532993 > @
        [] > l436532993
          prim__int.constructor_2 > @
            prim__int.new
            0
      prim__int.constructor_1 > state
        prim__int.new
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s511717113
      this
    [] > initialization
      this.init > @
        this
    [] > s511717113
      super.constructor > @
        this.super

[] > class__B
  class__A > super
  super > @
  [] > new
    [] > this
      class__A.new > super
      super > @
      "class__B" > className
      [this] > init
        seq > @
          TRUE
      # n :: int -> int
      [this x] > n
        seq > @
          s1219161283
        [] > s1219161283
          b1552978964 > @
        [] > b1552978964
          f_a355790875.add > @
            s_r2028017635
        [] > f_a355790875
          t782378927.state > @
        [] > t782378927
          this > @
        [] > s_r2028017635
          x > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s454325163
      this
    [] > initialization
      this.init > @
        this
    [] > s454325163
      super.constructor > @
        this.super
```
</details>


Analyzer output:

```
Method 'n' of object 'class__B.new.this' directly accesses state 'state' of base class 'class__A.new.this'
```

## Violation of the Liskov substitution principle

[(Back to TOC)](#polystat-cli)

This defect means that some parts of the code violate the [Liskov substitution principle](https://github.com/polystat/odin#liskov-substitution-principle-violation-analyzer).

Comes from: [polystat/odin](https://github.com/polystat/odin#liskov-substitution-principle-violation-analyzer)

Sample input (Java):

```java
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

```

<details>

<summary>

Show translation to EO (generated with J2EO v0.5.3)</summary>
```
# 2022-06-20T16:48:51.463254529
# j2eo team
+alias stdlib.lang.class__Object
+alias stdlib.primitives.prim__int
+alias org.eolang.gray.cage

[] > class__Parent
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Parent" > className
      [this] > init
        seq > @
          TRUE
      # f :: int -> int
      [this x] > f
        seq > @
          s873610597
        [] > s873610597
          s_r1497845528 > @
        [] > s_r1497845528
          x > @
      # g :: int -> int
      [this x] > g
        seq > @
          s1710989308
        [] > s1710989308
          m_i1047087935 > @
        [] > m_i1047087935
          this.f > @
            this
            s_r464887938
        [] > s_r464887938
          x > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s2020152163
      this
    [] > initialization
      this.init > @
        this
    [] > s2020152163
      super.constructor > @
        this.super

[] > class__Child
  class__Parent > super
  super > @
  [] > new
    [] > this
      class__Parent.new > super
      super > @
      "class__Child" > className
      [this] > init
        seq > @
          TRUE
      # f :: int -> int
      [this y] > f
        seq > @
          s1104443373
        [] > s1104443373
          b898694235 > @
        [] > b898694235
          l60292059.div > @
            s_r869601985
        [] > l60292059
          prim__int.constructor_2 > @
            prim__int.new
            10
        [] > s_r869601985
          y > @
    seq > @
      this
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1365008457
      this
    [] > initialization
      this.init > @
        this
    [] > s1365008457
      super.constructor > @
        this.super

[] > class__Test
  class__Object > super
  super > @
  [] > new
    [] > this
      class__Object.new > super
      super > @
      "class__Test" > className
      [this] > init
        seq > @
          TRUE
    seq > @
      this
  # main :: String[] -> void
  [args] > main
    seq > @
      d1671179293
      s1985836631
    cage > childInstance
    [] > d1671179293
      childInstance.write > @
        i_s1609124502
    [] > i_s1609124502
      inst1144068272 > @
    [] > inst1144068272
      class__Child.constructor > @
        class__Child.new
    [] > s1985836631
      childInstance.f > @
        childInstance
        l1948471365
    [] > l1948471365
      prim__int.constructor_2 > @
        prim__int.new
        10
  # null :: null -> void
  [this] > constructor
    seq > @
      initialization
      s1636506029
      this
    [] > initialization
      this.init > @
        this
    [] > s1636506029
      super.constructor > @
        this.super

[args...] > main
  class__Test.main > @
    *
```
</details>


Analyzer output:

```
Method f of object this violates the Liskov substitution principle as compared to version in parent object this
Method g of object this violates the Liskov substitution principle as compared to version in parent object new
```


## Division by zero

[(Back to TOC)](#polystat-cli)

The presence of this defect in the program means that some inputs may cause this program to fail with the ArithmeticException.

Comes from: [polystat/far](https://github.com/polystat/far)

__WARNING__: The FaR analyzer is not fully-integrated with J2EO translator so the defect detection may not work correctly. 


Sample input (simplified EO translation):
```
+package org.polystat.far

[a b] > fartest
  add. > @
    a.div b
    div.
      b.div a
      a
```

Analyzer output:
```
\\perp at {a=\\any, b=0}\n\\perp at {a=0, b=\\any}\n\\perp at {a=0, b=0}
```

# <a name="installation"></a> Installation

[(Back to TOC)](#polystat-cli)

## With coursier
If you have [coursier](https://get-coursier.io/docs/cli-installation) installed, then you can install the latest version of `polystat-cli` by running:
```
cs install --channel https://raw.githubusercontent.com/polystat/polystat-cli/master/coursier/polystat.json polystat
```
After that, you can simply run:
```
$ polystat --help
```

## Using a "fat" jar
The CLI is distributed as a "fat" jar (can be downloaded from [Github Releases](https://github.com/polystat/polystat-cli/releases)), so you can run without any prerequisites other than the [JRE](https://ru.wikipedia.org/wiki/Java_Runtime_Environment). If you have it installed, you can run `polystat-cli` by just executing:
```
$ java -jar polystat.jar <args>
```
It may be helpful to define an alias (the following works in most Linux and macos):
```
$ alias polystat="java -jar /path/to/polystat.jar"
```
And then simply run it like:
```
$ polystat <args>
```
More about the arguments you can pass can be found [here](#basic) and [here](#full).


# <a name="basic"></a> Basic usage

[(Back to TOC)](#polystat-cli)

* If no arguments are provided to `polystat`, it will read the configuration from the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) config file in the current working directory. The default name for this file is `.polystat.conf` in the current working directory.

```
$ polystat
```

* If you want to read the configuration from the file located elsewhere, the following command can be used:

```
$ polystat --config path/to/hocon/config.conf
```


* Print all the available configuration keys that can be used in the config file.  

```
$ polystat list -c
``` 

* Print the rule IDs for all the available analyzers. 

```
$ polystat list
```

* Don't execute some rules during the analysis. This option is repeatable, so you can add any number of `--exclude rule` arguments to exclude all the specified rules. In the example below all the rules **but** `mutualrec` and `long` will be executed.
```
$ polystat eo --in tmp --exclude mutualrec --exclude long --sarif
```

* Execute _only_ the given rules during the analysis. This option is also repeatable. 
In the example below **only** `mutualrec` and `liskov` rules will be executed. 

```
$ polystat eo --in tmp --include mutualrec --include liskov --sarif
```

* Get the plain text console output from analyzing Java files located in the directory `src/main/java`. 

```
$ polystat java --in src/main/java --console
```

* Write the SARIF JSON files to `polystat_out/sarif` from analysing the `tmp` directory with `.eo` files.


```
$ polystat eo --in tmp --sarif --to dir=polystat_out
```

# Running on big projects (hadoop)

[(Back to TOC)](#polystat-cli)

1. Clone the Hadoop `git` repository:
```sh
git clone https://github.com/apache/hadoop
```
2. Create the file `.polystat.conf` with the following contents:
```ini
polystat {
    lang = java
    input = hadoop
    tempDir = hadoop_tmp
    outputFormats = [sarif]
    outputs = {
        dirs = [hadoop_out],
        files = [hadoop.json]
    }
}
```
3. Run `polystat-cli` without arguments: 
```
$ polystat
```
or
```
$ java -jar polystat.jar
```
depending on which [installation method](#installation) you chose.

Executing these commands should create the following files:
1. `hadoop_tmp` should store all the temporary files produced by translators and analyzers.
2. `hadoop_out` should contain the produced `.sarif` files. Each `.sarif` file corresponds to a single `.java` file in the repository.
3. `hadoop.json` should contain the aggregated SARIF output for all the files in the repository. This `.json` file contains a single [`sarifLog`](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317478) object. This object has a property called [`runs`](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317482), which is an array of `run` objects. Each [`run`](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317484) object contains the name of the analyzed file and the [`results`](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317507) property, which holds the results of all the analyzers that completed successfully. 

# <a name="full"></a> Full Usage Specification

[(Back to TOC)](#polystat-cli)

This section covers all the options available in the CLI interface and their meanings. 

## Notation
The description follows [this guide](https://en.wikipedia.org/wiki/Command-line_interface#Command_description_syntax).
> Note: {a | b | c} means a set of _mutually-exclusive_ items.
```
polystat
    {eo | python}
    [--tmp <path>]
    [--in <path>]
    [{--include <rule...> | --exclude <rule...>}]
    [--sarif]
    [--to { console | dir=<path>| file=<path> }]...
polystat
    java
    [--j2eo-version <string>]
    [--j2eo <path>]
    [--tmp <path>]
    [--in <path>]
    [{--include <rule...> | --exclude <rule...>}]
    [--sarif]
    [--to { console | dir=<path>| file=<path> }]...
polystat [--version] [--help] [--config <path>]
polystat list [--config | -c]
```
## Input configuration
* The subcommand (`eo`, `java` or `python`) specifies which files should be analyzed (`.eo`, `.java` or `.py`). More languages can be added in the future. 
* `--in <file>` specifies the location of the source code to be analyzed. It can be either a directory with the files in the input language or a single file in the input language. If `--in` is not specified, defaults to reading the input language code from stdin.
* `--tmp <path>` specifies the path to the directory where the temporary files produced by analyzers are to be stored.  If `--tmp` is not specified, temporary files will be stored in the OS-created tempdir. It is assumed that the `path` supplied by `--tmp` points to an empty directory. If not, the contents of the `path` will be purged. If the `--tmp` option is specified but the directory it points to does not exist, it will be created. 
* The structure of the temporary directory is roughly as follows:
  * `<path>/eo` contains the generated `.eo` files.
  * `<path>/xmir` contains the generated `.xml` XMIR files (if any). 
  * `<path>/stdin` contains the files with the code read from stdin (if any).

## Configuration options
* <a name="inex"></a>`--include` and `--exclude` respectively define which rules should be included/excluded from the analysis run. These options are mutually exclusive, so specifying both should not be valid. If neither option is specified, all the available analyzers will be run. The list of available rule specifiers can be found via `polystat list` command.
* `--j2eo` (available only when running `polystat java`) option allows users to specify the path to the j2eo executable jar. If it's not specified, it looks for one in the current working diretory. 
If it's not present in the current working directory, download one from Maven Central (for now, the version is hardcoded to be 0.4.0).
* `--j2eo-version` (available only when running `polystat java`) option allows users to specify which version of `j2eo` should be downloaded.

## Output configuration
* `--sarif` option means that the command will produce the output in the [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) format in addition to output in other formats (if any). 
* `--to { console | dir=<path>| file=<path> }` is a repeatable option that specifies where the output should be written. If this option is not specified, no output is produced. 
* `--to dir=<path>` means that the files will be written to the given path. The path is assumed to be an empty directory. If it is not, its contents will be purged. If the `path` is specified but the directory it points to does not exist, it will be created. 
    * If an additional output format is specified (e.g. `--sarif`), then the files created by the analyzer will be written in the respective subdirectory. For example, in case of `--sarif`,  the SARIF files will be located in `path/sarif/`. The console output is not written anywhere. Therefore, if none of the output format options (e.g. `--sarif`) are specified, no files are produced. 
    * The output format options (e.g. `--sarif`) also determine the extension of the output files. In case of `--sarif` the extension would be `.sarif`.
    * If `--in` option specifies a directory, the structure of the output directory will be similar to the structure of the input directory. 
    * If `--in` specifies a single file, the file with the analysis output for this file will be written to the output directory. 
    * If `--in` is not specified, the generated file will be called `stdin` + the relevant extension. 

* `--to file=<path>` means that the results of analysis for all the files will be written to the file at the given path. For example, for `--sarif` output format this will a JSON array of `sarif-log` objects.

* `--to console` specifies whether the output should be written to console. The specification doesn't prevent the user from specifying multiple instances of this option. In this case, the output will be written to console as if just one instance of `--to console` was present. If it's not present the output is not written to console. 

## `polystat list`
* If no options are provided, prints the specifiers for all the available analyzer rules. 
* If `--config` or `-c` is specified, prints to console the descriptions of all the possible configuration keys for the HOCON config file.

## Other Options
* `--version` prints the version of `polystat-cli`, maybe with some additional information.
* `--help` displays some informative help messages for commands.
* `--config <path>` allows to configure Polystat from the specified HOCON config file. If not specified, reads configs from the file `.polystat.conf` in the current working directory.

# Configuration File
[(Back to TOC)](#polystat-cli)

This section covers all the keys that can be used in the HOCON configuration files. The most relevant version of the information presented in this section can be printed to console by running:
```
$ polystat list --config
```
The example of the working config file can be found [here](.polystat.conf).

* `polystat.lang` - the type of input files which will be analyzed. This key must be present. Possible values:
    * "java" - only ".java" files will be analyzed.
    * "eo" - only ".eo" files will be analyzed.
    * "python" - only ".py" files will be analyzed.
* `polystat.j2eoVersion` - specifies the version of J2EO to download.
* `polystat.j2eo` - specifies the path to the J2EO executable. If not specified, defaults to looking for j2eo.jar in the current working directory. If it's not found, downloads it from maven central. The download only happens when this key is NOT provided. 
* `polystat.input` - specifies how the files are supplied to the analyzer. Can be either a path to a directory, path to a file, or absent. If absent, the code is read from standard input.
* `polystat.tempDir` - the path to a directory where temporary analysis file will be stored. If not specified, defaults to an OS-generated temporary directory.
* `polystat.outputTo` - the path to a directory where the results of the analysis are stored. If not specified, the results will be printed to console.
* `polystat.outputFormats` - the formats for which output is generated. If it's an empty list or not specified, no output files are produced.
* `polystat.includeRules` | `polystat.excludeRules` - specified which rules should be included in / excluded from the analysis. If both are specified, polystat.includeRules takes precedence. The list of available rule specifiers can be found by running:
    ```
    $ polystat.jar list
    ```
* `polystat.outputs.console` - specifies if the analysis results should be output to console. `false` by default.
* `polystat.outputs.dirs` - a list of directories to write files to.
* `polystat.outputs.files` - a list of files to write aggregated output to. 

# Development

[(Back to TOC)](#polystat-cli)

Polystat CLI is an sbt Scala project. In order to build the project you need the following:
  * [JDK](https://ru.wikipedia.org/wiki/Java_Development_Kit) 8+
  * [sbt](https://www.scala-sbt.org/) 1.6.2

Both can be easily fetched via [coursier](https://get-coursier.io/docs/overview). 

Running the CLI:
```
$ sbt run
```

It's best to run this command in the interactive mode, because you can specify the cmdline args there.
However, for better turnaround time, it's better to tailor the `.polystat.conf` in the repository root for your needs and just run `run`.
If you want to change the command-line arguments, edit the `.polystat.conf` in the repository root.

The following command can be used to generate the "fat" JAR file. 
```
$ sbt assembly
```

The generated `.jar` file can be then found at `target/scala-3.1.2/polystat.jar`.

To run the tests use the relevant `sbt` task:
```
$ sbt test
```
