import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger; // added for Task 2 (multi-core)

public class task1 {
    static List<taskThread> readyQueue = new ArrayList<>();
    static int clock = 0;
    static Semaphore queueLock = new Semaphore(1); // single key to the ready queue
    static List<taskThread> notArrivedYet = new ArrayList<>(); // Task 1 PSJF: late arrivals

    static Semaphore availableTasks = new Semaphore(0);
    static AtomicInteger tasksLeft = new AtomicInteger(0);
    static int CORES = 4;
    static int algorithm = 1; // multi-core only: 1 = FCFS, 2 = RR, 3 = NSJF
    static int quantum = 5;   // multi-core RR time quantum

    static void cpu(taskThread task, int slice, String algo) throws InterruptedException {
        task.allotted = slice;
        System.out.println("CPU: running T" + task.id + " for " + task.allotted + " laps.");
        while (task.allotted > 0 && task.remaining > 0) {
            task.go.release(); //green light
            task.done.acquire(); // red light
            task.remaining--; //reduce by one lap
            task.allotted--; //do one less lap
            clock++; //keep the clock going
            if (algo.equals("PSJF")) {
                checkArrivals(); // did anyone arrive?
                if (task.remaining > 0 && shorterCarWaiting(task)) {
                    System.out.println("Lap " + clock + ": PREEMPT T" + task.id
                            + " (" + task.remaining + " left) — shorter car waiting");
                    break; // wave the current car into the pit, re-pick next loop
                }
            }
        }
        System.out.println("CPU: T" + task.id + " stopped, " + task.remaining + " laps left.");
    }

    // ---- Task 2 multi-core CPU (clock guarded so 4 cores don't clobber it) — buddy ----
    static void cpu(taskThread task, int slice) throws InterruptedException {
        task.allotted = slice;
        System.out.println("CPU: running T" + task.id + " for " + task.allotted + " laps.");
        while (task.allotted > 0 && task.remaining > 0) {
            task.go.release();    // green light
            task.done.acquire();  // red light
            task.remaining--;
            task.allotted--;
            synchronized (task1.class) {
                clock++;
            }
        }
        System.out.println("CPU: T" + task.id + " stopped, " + task.remaining + " laps left.");
    }

    public static void main(String[] args) throws InterruptedException {
        // === MODE SWITCH (temporary — step 9 will read this from args) ===
        // false = Drayke's single-core Task 1 (default, verified)
        // true  = buddy's multi-core Task 2
        boolean MULTICORE = false;

        Random rand = new Random();
        int T = rand.nextInt(25) + 1;

        if (MULTICORE) {
            // ---- Task 2 multi-core startup — buddy ----
            for (int i = 0; i < T; i++) {
                taskThread task = new taskThread(i, rand.nextInt(50) + 1);
                System.out.println("Created task " + task.id + " burst " + task.burst);
                readyQueue.add(task);
                Thread th = new Thread(task);
                th.start();
            }
            System.out.println("Ready queue: " + readyQueue);
            tasksLeft.set(T);
            availableTasks.release(T);
            startMultiCore();
            return;
        }

        // ---- Task 1 single-core startup (PSJF) — Drayke ----
        String algo = "PSJF";
        int quantum = 5;

        for(int i = 0; i < T; i++) {
            taskThread task = new taskThread(i, rand.nextInt(50)+1);
            System.out.println("Created task " + task.id + " burst " + task.burst);
            queueLock.acquire(); // grab the key before touching the lists
            if (algo.equals("PSJF") && i > 0) { // PSJF: only T0 starts
                task.arrival = rand.nextInt(30) + 1; // arrives at lap 1 to 30
                notArrivedYet.add(task);
                System.out.println("T" + task.id + " will arrive at lap " + task.arrival);
            } else {
                readyQueue.add(task); // here from the start
            }
            queueLock.release();
            Thread th = new Thread(task);
            th.start();
        }
        System.out.println("Ready queue: " + readyQueue);

        Thread director = new Thread(new dispatcherThread(algo, quantum));
        director.start();
        director.join();

        System.out.println("All tasks finished. Total cycles: " + clock);
    }

    static taskThread fcfs () {return readyQueue.remove(0);}
    static taskThread rr () {return readyQueue.remove(0);}
    static taskThread nsjf() {
        taskThread shortest = readyQueue.get(0); //assumes the first is shortest to begin
        for (taskThread t: readyQueue) {
            if (t.remaining < shortest.remaining) {
                shortest = t;  // shorter one found
            }
        }
        readyQueue.remove(shortest);  // remove the shortest for execution turn
        return shortest; // return it
    }
    static taskThread psjf() {return nsjf();} // works the same as nsjf here so just reuse it


    static void checkArrivals() throws InterruptedException {
        queueLock.acquire();
        List<taskThread> arrivedNow = new ArrayList<>(); // who showed up
        for (taskThread t : notArrivedYet) {
            if (t.arrival <= clock) {
                arrivedNow.add(t);
            }
        }
        for (taskThread t : arrivedNow) { // second loop so I don't remove while looping the same list
            notArrivedYet.remove(t);
            readyQueue.add(t);
            System.out.println("Lap " + clock + ": T" + t.id + " arrived, needs " + t.remaining + " laps");
        }
        queueLock.release();
    }

    static boolean shorterCarWaiting(taskThread running) throws InterruptedException {
        queueLock.acquire();
        boolean found = false;
        for (taskThread t : readyQueue) {
            if (t.remaining < running.remaining) {
                found = true; // someone quicker is waiting
                break;
            }
        }
        queueLock.release();
        return found;
    }

    static void dispatcher(String algo, int quantum) throws InterruptedException {
        System.out.println("Dispatcher: algorithm selected is " + algo);

        while(true) {
            queueLock.acquire();
            if (readyQueue.isEmpty() && notArrivedYet.isEmpty()) {
                queueLock.release(); // everyone's done and no one left to arrive
                break;
            }
            if (readyQueue.isEmpty()) {
                queueLock.release(); // give the key back first (checkArrivals grabs it too)
                clock++;
                System.out.println("Dispatcher: pit lane empty, idle lap " + clock);
                checkArrivals();
                continue;
            }

            System.out.println("Ready queue: " + readyQueue);
            taskThread task;

            if (algo.equals("RR")) {
                task = rr();
            } else if (algo.equals("NSJF")) {
                task = nsjf();
            } else if (algo.equals("PSJF")) {
                task = psjf();
            } else {
                task = fcfs();
            }
            queueLock.release(); // Releasing the key here so the readyQueue can continue

            int slice;
            if(algo.equals("RR")) {
                slice = quantum;
            } else {
                slice = task.remaining;
            }
            if(slice > task.remaining) {
                slice = task.remaining;
            }
            System.out.println("Dispatcher: selected T" + task.id + ", gets " + slice + " laps");

            cpu(task,slice,algo);

            if (task.remaining > 0) {
                // Not done: back of the line
                queueLock.acquire();
                readyQueue.add(task);
                queueLock.release();
                System.out.println("Dispatcher: T" + task.id + " back in line, " + task.remaining + " laps left");
            } else {
                System.out.println("Dispatcher: T" + task.id + " finished");
            }        }
    }

    // ===== Task 2 (multi-core) dispatcher + startup — buddy's code, kept intact =====

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

}

class taskThread implements Runnable {
    int id;
    int burst;
    int remaining;
    int allotted;
    int arrival;
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
        this.arrival = 0;
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

class dispatcherThread implements Runnable {
    String algo; // which rules to use
    int quantum;

    dispatcherThread(String algo, int quantum) {
        this.algo = algo;
        this.quantum = quantum;
    }

    public void run() {
        try {
            task1.dispatcher(algo, quantum); // do the director's job
        } catch (InterruptedException e) {
            return;
        }
    }
}