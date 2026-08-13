package com.hmdp.LeetCode;

public class LC {
    public int[] sortedSquares(int[] nums){
        int[] result = new int[nums.length];
        int k= result.length-1;
        int left=0;
        int right= nums.length-1;

        while (left<=right){
            if(nums[left]*nums[left]<nums[right]*nums[right]){
                result[k] =nums[right]*nums[right];
                k--;
                right--;
            }else{
                result[k]=nums[left]*nums[left];
                k--;
                left++;
            }
        }

        return result;


    }
    public int minSubArrayLen(int target, int[] nums){

        int end=0;
        int start=0;
        int sum=0;
        int min=nums.length;

        int total=0;
        for (int i = 0; i < nums.length; i++) {
            total+=nums[i];
        }
        if(total<target){
            return 0;
        }

        while (start<=end){
            if(sum>=target){
                int len = end - start;
                if(len<min){
                    min=len;
                }
                sum=sum-nums[start];
                start++;
            }else{
                if(end< nums.length-1){
                    end++;
                    sum=sum+nums[end];
                }else {
                    break;
                }
            }
        }


        return min;
    }

}
