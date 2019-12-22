package de.dosmike.sponge.oregeno.util;

public interface AmountComparator {
    AmountComparator EXACT = (a,b)->a==b;
    AmountComparator NOT = (a,b)->a!=b;
    AmountComparator LESS = (a,b)->a<b;
    AmountComparator LESSEQUAL = (a,b)->a<=b;
    AmountComparator GREATER = (a,b)->a>b;
    AmountComparator GREATEREQUAL = (a,b)->a>=b;

    boolean compares(int a, int b);
}
