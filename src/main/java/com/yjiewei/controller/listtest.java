package com.yjiewei.controller;

import java.util.*;

/**
 * @author johnny
 * @Classname listtest
 * @Description
 * @Date 2022/1/12 3:04 下午
 */
public class listtest {
    public static void main(String[] args) {
//        List<Integer> list=new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            list.add(i);
//        }
//        for (int i = 0; i <5 ; i++) {
//            list.add(i);
//        }
//        System.out.println("arraylist===");
//        for (Integer integer : list) {
//            System.out.println(integer);
//        }

        HashSet set = new HashSet<>();
        set.add(1);
        set.add(2);
        set.add(3);
        set.add("johnny");
        set.add("mary");
        set.add("johnny");
        set.add(null);
        set.add(null);
        System.out.println(set);


    }
}
