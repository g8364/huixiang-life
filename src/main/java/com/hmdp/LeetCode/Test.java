package com.hmdp.LeetCode;

public class Test {
    public static void main(String[] args) {
//        int[] nums={-4,-1,0,3,10};
        int[] nums={2,1,4,3,2,3};

        LC ops=new LC();
//        int[] res = ops.sortedSquares(nums);
//        for (int i = 0; i < res.length; i++) {
//            System.out.println(res[i]);
//        }
        int min = ops.minSubArrayLen(7, nums);
        System.out.println(min);

    }
}
