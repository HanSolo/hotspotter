package sample;

/** Test app with known call counts. */
public class Main {

    static long sink;

    static void hot(int i) {
        sink += i;
    }

    static void cold() {
        sink++;
    }

    static class Worker {
        long work(long[] a, String s) {
            return a.length + s.length();
        }
    }

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        Worker w = new Worker();
        long[] arr = new long[3];
        // per round: 1000 x hot, 10 x cold, 100 x Worker.work
        for (int round = 0; round < seconds * 10; round++) {
            for (int i = 0; i < 1000; i++) {
                hot(i);
            }
            for (int i = 0; i < 10; i++) {
                cold();
            }
            for (int i = 0; i < 100; i++) {
                sink += w.work(arr, "abc");
            }
            Thread.sleep(100);
        }
        System.out.println("done " + sink);
    }
}
