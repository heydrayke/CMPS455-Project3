
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class task1 {

    static List<taskThread> readyQueue = new ArrayList<>();
    static int clock = 0;

    // Begin Task 2 code changes

    static Semaphore queueLock = new Semaphore(1);
    static Semaphore availableTasks = new Semaphore(0);

    static AtomicInteger tasksLeft = new AtomicInteger(0);

    static int CORES = 4;

    // 1 = FCFS, 2 = RR, 3 = NSJF
    static int algorithm = 1;

    // Round Robin time quantum
    static int quantum = 5;

    // End Task 2 code changes



    static void cpu(taskThread task, int slice)
            throws InterruptedException {

        task.allotted = slice;

        System.out.println(
                "CPU: running T" + task.id +
                        " for " + task.allotted + " laps."
        );

        while (task.allotted > 0 && task.remaining > 0) {

            task.go.release();    // green light
            task.done.acquire();  // red light

            task.remaining--;
            task.allotted--;

            // Begin Task 2 code changes
            synchronized (task1.class) {
                clock++;
            }
            // End Task 2 code changes
        }

        System.out.println(
                "CPU: T" + task.id +
                        " stopped, " + task.remaining + " laps left."
        );
    }


    public static void main(String[] args)
            throws InterruptedException {

        Random rand = new Random();

        int T = rand.nextInt(25) + 1;

        // Begin Task 2 code changes
        tasksLeft.set(T);
        // End Task 2 code changes

        for (int i = 0; i < T; i++) {

            taskThread task = new taskThread(
                    i, rand.nextInt(50) + 1
            );

            System.out.println(
                    "Created task " + task.id +
                            " burst " + task.burst
            );

            readyQueue.add(task);

            Thread th = new Thread(task);
            th.start();
        }

        System.out.println("Ready queue: " + readyQueue);

        // Begin Task 2 code changes

        availableTasks.release(T);


        startMultiCore();

        // End Task 2 code changes
    }

    static void fcfs() {}

    static void rr() {}

    static void nsjf() {}

    static void psjf() {}

    static void dispatcher() {}



    // Begin Task 2 code changes

    static void multiCoreDispatcher(int coreId) {

        try {

            while (true) {


                availableTasks.acquire();

                queueLock.acquire();

                if (tasksLeft.get() == 0) {
                    queueLock.release();
                    break;
                }

                taskThread task;

                if (algorithm == 3) {



                    task = Collections.min(
                            readyQueue,
                            Comparator.comparingInt(t -> t.remaining)
                    );

                    readyQueue.remove(task);

                } else {


                    task = readyQueue.remove(0);
                }

                System.out.println(
                        "CPU " + coreId +
                                " selected T" + task.id
                );

                System.out.println(
                        "Ready queue: " + readyQueue
                );

                queueLock.release();

                int slice;

                if (algorithm == 2) {


                    slice = Math.min(
                            quantum, task.remaining
                    );

                } else {


                    slice = task.remaining;
                }

                System.out.println(
                        "CPU " + coreId +
                                " running T" + task.id +
                                " for " + slice + " laps"
                );

                cpu(task, slice);

                queueLock.acquire();

                if (task.remaining > 0) {

                    readyQueue.add(task);

                    System.out.println(
                            "T" + task.id +
                                    " returned to ready queue"
                    );
                    availableTasks.release();
                } else {

                    System.out.println(
                            "T" + task.id + " completed"
                    );


                    if (tasksLeft.decrementAndGet() == 0) {

                        availableTasks.release(CORES);
                    }
                }

                queueLock.release();
            }

            System.out.println(
                    "CPU " + coreId +
                            " dispatcher finished"
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.out.println(
                    "CPU " + coreId + " interrupted"
            );
        }
    }



    static void startMultiCore()
            throws InterruptedException {

        System.out.println(
                "\nStarting Multi-Core CPU Scheduling"
        );

        String name;

        if (algorithm == 1) {
            name = "FCFS";
        }
        else if (algorithm == 2) {
            name = "Round Robin";
        }
        else {
            name = "Non-Preemptive SJF";
        }

        System.out.println("Algorithm: " + name);
        System.out.println("CPU Cores: " + CORES);

        if (algorithm == 2) {
            System.out.println(
                    "Time Quantum: " + quantum
            );
        }

        Thread[] dispatchers = new Thread[CORES];

        for (int i = 0; i < CORES; i++) {

            final int coreId = i + 1;

            dispatchers[i] = new Thread(() -> {
                multiCoreDispatcher(coreId);
            });

            dispatchers[i].start();
        }

        for (int i = 0; i < CORES; i++) {
            dispatchers[i].join();
        }

        System.out.println(
                "\nAll CPU cores finished."
        );

        System.out.println(
                "Total CPU laps: " + clock
        );
    }

    // End Task 2 code changes

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

                go.acquire();

                System.out.println(
                        "Task " + id +
                                ": lap " + cycle + "/" + burst
                );

                done.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
}
