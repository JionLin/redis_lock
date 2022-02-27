package com.yjiewei.controller;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author yjiewei
 * @date 2021/8/5
 */
@RestController
public class IndexController {

    @Resource
    private Redisson redisson;

    @Resource
    private StringRedisTemplate stringRedisTemplate; // 居然根据名字来找的，不是根据类型。。。

    /**
     * 代码有什么问题？
     * 1.线程安全问题，多个线程同时访问就可能出现错误，用synchronized可以解决但是性能不好
     * synchronized在高并发情况下还是有bug出现，会出现超卖，可以用jmeter压测
     * 2.设置redis锁解决分布式场景之后，超时时间设置10s合理吗？适合场景问题？如果10s中之内没有处理完，处理到一半呢？
     * 15s--10s后释放锁--还需要5s，5s后释放了其他人的锁
     * 8s--5s后我的锁被人释放了，其他线程又来了
     * 循环下去，锁的是别人....这不就完全乱套了，这锁完全没用啊
     * 解决方法：你不是可能存在释放别人的锁的情况吗？那就设置识别号，识别到只能是自己的才能被释放
     * 这只是解决了释放别人的锁的问题，你自己没有执行完就已经超时的问题呢？
     * 答案：开启子线程定时器来延长超时时间咯，子线程每隔一段时间就去查看是否完成，没完成就加时，那这个一段时间要多长呢？
     * 三分之一过期时间，其他人的实践经验。
     * 所以：我们现在又要造轮子了吗？是否有其他人已经考虑过这个问题并做开源了呢？
     * 那肯定有啊，不然我写这个干吗。redisson，比jedis强大，专对分布式
     * <p>
     * 3.redisson
     * 大概阐述一下这个锁的操作：
     * 当一个redisson线程过来获取到锁时，后台会有其他线程去检查是否还持有锁，
     * 还持有说明还没执行结束，就会继续延长锁的时间，大概10s去轮询。（三分之一）
     * 另外一个线程过来，如果没有获取锁成功，就会while自旋尝试加锁。
     * clientId他在底层实现了。
     * <p>
     * 3.1如果使用的是Redis主从架构呢，主节点宕了，从节点怎么处理？但这是锁还没有被同步过去，其他线程就过来访问了呢？
     * 3.2另外如何提升性能呢？
     * - 商品库存分段存储，key不一样，每个段的数量越小性能不就越高嘛，而且锁定不同的key值
     *
     * @return
     */
    @RequestMapping("/deduct_stock")
    public String deductStock() {

        Map<String, Object> map = new HashMap<>();
        // 0.标识号
        String clientID = UUID.randomUUID().toString();

        // 1.这个相当于一把锁，控制只能一个人来
        String lockKey = "product";
        RLock lock = redisson.getLock(clientID); // 3.1
        long startMillis = 0;
        long endMillis = 0;
        Map map2 = new HashMap();
        try {

/*
            // 2.获取锁
            Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "yjiewei");// jedis.setnx(key, value);
            // 3.如果突发宕机，锁没有释放掉怎么办，key过期处理(10s)，但是如果在获取锁之后就出问题呢，这一步也没有成功，大招：二合一
            stringRedisTemplate.expire(lockKey, 10, TimeUnit.SECONDS);
*/

            // 2-3
/*          Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, clientID, 10, TimeUnit.SECONDS);
            if (!result){
                return "error";
            }
*/

            // 3.1 解决过期时间内还未完成操作的问题
            lock.lock(30, TimeUnit.SECONDS); // 先拿锁，再设置超时时间
            startMillis = System.currentTimeMillis();

            // 4.真正操作商品库存
            synchronized (this) {
                int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("stock")); // jedis.get("stock");
                if (stock > 0) {
                    int realStock = stock - 1;
                    stringRedisTemplate.opsForValue().set("stock", realStock + ""); // jedis.set(key, value);
                    System.out.println("扣减成功，剩余库存：" + realStock);
                } else {
                    System.out.println("扣减失败，库存不足！");
                }
            }
            endMillis = System.currentTimeMillis();
        } finally {
            lock.unlock(); // 释放锁
/*
            if (clientID.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
                // 5.释放锁，放在finally块中防止没有释放锁导致死锁问题
                stringRedisTemplate.delete(lockKey);
            }
*/
            System.out.println("200个库存 同步时，耗时: " + (endMillis - startMillis));
        }
        return "end";
    }


    @RequestMapping("/test")
    public void test() {
        for (int i = 0; i < 400; i++) {
            stringRedisTemplate.opsForValue().set(String.valueOf(i), String.valueOf(i + 4), 1000, TimeUnit.SECONDS);
        }
    }


    public static void main(String[] args) {


        LinkedList linkedList = new LinkedList();
        for (int i = 0; i < 10; i++) {
            linkedList.add(i);
        }

        for (int i = 11; i < 15; i++) {
            linkedList.add(i);
        }
        linkedList.add(100);
        linkedList.add(200);
        linkedList.add(null);

        System.out.println("linkedList===" + linkedList);


        System.out.println("===============");

//        ArrayList arrayList=new ArrayList<>();
        ArrayList arrayList = new ArrayList<>(8);
        for (int i = 0; i < 10; i++) {
            arrayList.add(i);
        }

        for (int i = 11; i < 15; i++) {
            arrayList.add(i);
        }
        arrayList.add(100);
        arrayList.add(200);
        arrayList.add(null);

        for (Object o : arrayList) {
            System.out.println(o);
        }


        /**
         * 1 进行解读 add 方法 无参 构造
         *   此时size 为0    private int size;
         *  public boolean add(E e) {
         *         ensureCapacityInternal(size + 1);  // Increments modCount!!
         *         elementData[size++] = e;
         *         return true;
         * 2.走 ensureCapacityInternal  确定容量内部方法
         *   private void ensureCapacityInternal(int minCapacity) {
         *         ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
         * 3.走 ensureExplicitCapacity 确保显式容量扩容方法  来进行比较 是否为空数组,如果为空,返回默认的初始容量10出去
         *       private static final int DEFAULT_CAPACITY = 10;
         *  private static int calculateCapacity(Object[] elementData, int minCapacity) {
         *         if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
         *             return Math.max(DEFAULT_CAPACITY, minCapacity);
         *         return minCapacity;
         * 4.如果最小容量大于数组容量 就进行扩容 确保明确的能力
         *  private void ensureExplicitCapacity(int minCapacity) {
         *         modCount++;
         *         if (minCapacity - elementData.length > 0)
         *             grow(minCapacity);
         * 5.调用真正扩容方法
         *  1.获取数组的长度 2.然后数组长度+数组长度右移1位  获取新的容量大小 以1.5倍算 3.用新的容量大小和传递进来的容量大小进行比较,如果新容量大小-最小容量大小小于0
         *  那么最小容量就赋值为新容量大小 4.然后进行扩容,把元素大小扩容为新容量大小
         *  private void grow(int minCapacity) {
         *         // overflow-conscious code
         *         int oldCapacity = elementData.length;
         *         int newCapacity = oldCapacity + (oldCapacity >> 1);
         *         if (newCapacity - minCapacity < 0)
         *             newCapacity = minCapacity;
         *         if (newCapacity - MAX_ARRAY_SIZE > 0)
         *             newCapacity = hugeCapacity(minCapacity);
         *         // minCapacity is usually close to size, so this is a win:
         *         elementData = Arrays.copyOf(elementData, newCapacity);
         *     }
         */


        Vector vector = new Vector();
        for (int i = 0; i < 10; i++) {
            vector.add(i);
        }

        for (int i = 11; i < 15; i++) {
            vector.add(i);
        }
        vector.add(100);
        vector.add(200);
        vector.add(null);

        for (Object o : vector) {
            System.out.println(o);
        }
        /**
         *  Vector add 方法
         *  1.无参构造,默认初始化为10
         *  2. 当需要扩充的时候, 需要进行当前数值翻倍
         *    public Vector() {
         *         this(10);
         *     }
         *      public Vector(int initialCapacity) {
         *         this(initialCapacity, 0);
         *     }
         private void grow(int minCapacity) {
         int oldCapacity = elementData.length;
         int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
         capacityIncrement :
         private void grow(int minCapacity) {
         int oldCapacity = elementData.length;
         int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
         capacityIncrement : oldCapacity);
         if (newCapacity - minCapacity < 0)
         newCapacity = minCapacity;
         if (newCapacity - MAX_ARRAY_SIZE > 0)
         newCapacity = hugeCapacity(minCapacity);
         elementData = Arrays.copyOf(elementData, newCapacity);
         });
         if (newCapacity - minCapacity < 0)
         newCapacity = minCapacity;
         if (newCapacity - MAX_ARRAY_SIZE > 0)
         newCapacity = hugeCapacity(minCapacity);
         elementData = Arrays.copyOf(elementData, newCapacity);
         }
         */

        Node jack = new Node("jack");
        Node tome = new Node("tom");
        Node johnny = new Node("johnny");

        // 从头到尾执行
        jack.next = tome;
        tome.next = johnny;

        johnny.pre = tome;
        tome.pre = jack;

        Node first = jack;

        Node last = johnny;

        System.out.println("从前往后");
        // 从前往后
        while (true) {
            if (first == null) {
                break;
            }
            System.out.println(first);
            first = first.next;
        }

        System.out.println("从后往前");
        //从尾到头执行
        while (true) {
            if (last == null) {
                break;
            }
            System.out.println(last);
            last = last.pre;
        }


        // 在tome 和johnny 之间插入一个数据
        // jack-tome-johnny
        //johnny -tome -jack
        // 新建一个节点
        Node smith = new Node("smith");
        tome.next=smith;
        smith.next=johnny;

        johnny.pre=smith;
        smith.pre=tome;


         first = jack;

         last = johnny;

        System.out.println("从前往后2");
        // 从前往后
        while (true) {
            if (first == null) {
                break;
            }
            System.out.println(first);
            first = first.next;
        }

        System.out.println("从后往前2");
        //从尾到头执行
        while (true) {
            if (last == null) {
                break;
            }
            System.out.println(last);
            last = last.pre;
        }


        LinkedList linkedList1=new LinkedList();

        linkedList1.add(1);
        linkedList1.add(2);
        linkedList1.add(1);


        /**
         *   linkedList1.add(1); add 方法 进行尾插入法
              public boolean add(E e) {
                    linkLast(e);
                    return true;
                }
               // 进行链接  先把last 赋值给l  然后新建一个节点,把传入的数放到item 里面, 然后把新的数据给last
               * 来判断l 是否为空,如果为空,那就指向first节点
               * 如果l不为空,代表有值,把l的next节点指向新的节点
                  void linkLast(E e) {
                        final Node<E> l = last;
                        final Node<E> newNode = new Node<>(l, e, null);
                        last = newNode;
                        if (l == null)
                            first = newNode;
                        else
                            l.next = newNode;
                        size++;
                        modCount++;
                    }
         *
         */

        linkedList1.remove();
        /**
         * 进行删除  删除第一个
         *   public E remove() {
         *         return removeFirst();
         *     }
         *     实际调用的是 unlinkFirst 方法
         *      public E removeFirst() {
         *         final Node<E> f = first;
         *         if (f == null)
         *             throw new NoSuchElementException();
         *         return unlinkFirst(f);
         *     }
         *     // 最重要的这个方法,来给它进行截断 .item=null；.next=null .prev=null
         *      private E unlinkFirst(Node<E> f) {
         *         // assert f == first && f != null;
         *         final E element = f.item;
         *         final Node<E> next = f.next;
         *         f.item = null;
         *         f.next = null; // help GC
         *         first = next;
         *         if (next == null)
         *             last = null;
         *         else
         *             next.prev = null;
         *         size--;
         *         modCount++;
         *         return element;
         */
    }

}

class Node {

    public Object item;

    public Node next;

    public Node pre;

    public Node(Object item) {
        this.item = item;
    }

    @Override
    public String toString() {
        return "node name=" + item;
    }
}