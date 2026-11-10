import java.util.*;
import java.util.concurrent.Semaphore;

public class task1 {
    static List<taskThread> readyQueue = new ArrayList<>();
    static int clock = 0;
    static void cpu(taskThread task, int slice) throws InterruptedException {
        task.allotted = slice;
        System.out.println("CPU: running " + task.id + " for " + task.allotted + " laps.");
        while (task.allotted > 0 && task.remaining > 0) {
            task.go.release(); //green light
            task.done.acquire(); // red light
            task.remaining--; //reduce by one lap
            task.allotted--; //do one less lap
            clock++; //keep the clock going
        }
        System.out.println("CPU: T" + task.id + " stopped, " + task.remaining + " laps left.");
    }

    public static void main(String[] args) throws InterruptedException {
        Random rand = new Random();
        int T = rand.nextInt(25) + 1;

        for(int i = 0; i < T; i++) {
           taskThread task = new taskThread(i, rand.nextInt(50)+1);
           System.out.println("Created task " + task.id + " burst " + task.burst);
           readyQueue.add(task);
           Thread th = new Thread(task);
           th.start();
        }
        System.out.println("Ready queue: " + readyQueue);
        cpu(readyQueue.get(0),5);
    }

    static void fcfs () {}
    static void rr () {}
    static void nsjf() {}
    static void psjf() {}
    static void dispatcher() {}

}

class taskThread implements Runnable {
    int id;
    int burst;
    int remaining;
    int allotted;
    Semaphore go = new Semaphore(0);
    Semaphore done = new Semaphore(0);

    public String toString() {
        return "T" + id + "(" + remaining + ")";
    }



    taskThread(int id, int burst) {
        this.id = id;
        this.burst = burst;
        this.remaining = burst;
        this.allotted = 0;
    }
    public void run() {
        try {
            for (int cycle = 1; cycle <= burst; cycle++) {
                go.acquire(); // wait for CPU session to begin
                System.out.println("Task " + id + ": lap " + cycle + "/" + burst);
                done.release(); // done with session
            }
        } catch (InterruptedException e) {
            return;
        }
    }
}
