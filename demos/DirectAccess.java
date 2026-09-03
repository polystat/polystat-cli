/*
 * SPDX-FileCopyrightText: Copyright (c) 2022 Polystat
 * SPDX-License-Identifier: MIT
 */
class A {
    protected int state = 0;
};

class B extends A {
    public int n(int x) {
        return this.state + x;
    }
}
