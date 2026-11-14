import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class task1 {
    static List<taskThread> readyQueue = new ArrayList<>();
    static int clock = 0;
    static Semaphore queueLock = new Semaphore(1); // This is needed to ensure that two threads trying to get added to the readyQuee don't overwrite each other
    static List<taskThread> notArrivedYet = new ArrayList<>(); // for threads being produced on the fly

    static Semaphore availableTasks = new Semaphore(0);
    static AtomicInteger tasksLeft = new AtomicInteger(0);
    static int CORES = 1; // set from the -C command line argument, defaults to 1
    static int algorithm = 1; // 1 = FCFS, 2 = RR, 3 = NSJF, 4 = PSJF (set from -S)
    static int quantum = 5; // Round Robin time quantum (set from -S 2 <quantum>)

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
                    break; // unfinished car goes back in line, re-pick next loop
                }
            }
        }
        System.out.println("CPU: T" + task.id + " stopped, " + task.remaining + " laps left.");
    }

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

    static final String[] ALGO_NAMES = {"FCFS", "RR", "NSJF", "PSJF"};

    // Method to check input arguments
    // Returns -1 if any invalid inputs detected. If valid, returns the algorithm number (1-4)
    // and sets quantum (when round-robin) and CORES (when -C is given)
    static int checkArg(String[] args) {

        // An algorithm must be selected, error if no arguments
        if (args.length == 0) {
            System.out.println("\nError: No arguments given.");
            return -1;
        }

        int alg = -1;
        int cores = 1; // -C is optional, default to a single core
        int q = quantum;
        boolean sSeen = false;
        boolean cSeen = false;

        // -S and -C can come in any order, so walk the arguments one flag at a time
        int i = 0;
        while (i < args.length) {
            String flag = args[i];

            if (flag.equals("-S")) {
                // Error if -S is given more than once
                if (sSeen) {
                    System.out.println("\nError: -S given more than once.");
                    return -1;
                }
                sSeen = true;

                // Error if flag is -S but no algorithm number
                if (!hasValue(args, i + 1)) {
                    System.out.println("\nError: -S requires a value.");
                    return -1;
                }

                alg = parseExact(args[i + 1], 1, 4);
                if (alg < 0) {
                    System.out.println("\nError: '" + args[i + 1] + "' is not valid. Algorithm must be 1, 2, 3, or 4.");
                    return -1;
                }
                i += 2;

                // Round-robin needs a second parameter, the time quantum
                if (alg == 2) {
                    if (!hasValue(args, i)) {
                        System.out.println("\nError: -S 2 (round-robin) requires a time quantum.");
                        return -1;
                    }
                    q = parseExact(args[i], 2, 10);
                    if (q < 0) {
                        System.out.println("\nError: '" + args[i] + "' is not valid. Time quantum must be an integer from 2 to 10.");
                        return -1;
                    }
                    i++;

                    // Round-robin only takes the algorithm number and the quantum
                    if (hasValue(args, i)) {
                        System.out.println("\nError: Too many values for -S 2.");
                        return -1;
                    }
                }
                // Only round-robin takes a second parameter
                else if (hasValue(args, i)) {
                    System.out.println("\nError: Too many values for -S " + alg + ". Only round-robin (-S 2) takes a time quantum.");
                    return -1;
                }
            }
            else if (flag.equals("-C")) {
                // Error if -C is given more than once
                if (cSeen) {
                    System.out.println("\nError: -C given more than once.");
                    return -1;
                }
                cSeen = true;

                // Error if flag is -C but no core count
                if (!hasValue(args, i + 1)) {
                    System.out.println("\nError: -C requires a value.");
                    return -1;
                }

                cores = parseExact(args[i + 1], 1, 4);
                if (cores < 0) {
                    System.out.println("\nError: '" + args[i + 1] + "' is not valid. Cores must be 1, 2, 3, or 4.");
                    return -1;
                }
                i += 2;

                // -C only takes one parameter
                if (hasValue(args, i)) {
                    System.out.println("\nError: Too many values for -C.");
                    return -1;
                }
            }
            else {
                System.out.println("\nError: " + (flag.isEmpty() ? "''" : flag) + " not a known flag.");
                return -1;
            }
        }

        // -S is required even when -C is given
        if (!sSeen) {
            System.out.println("\nError: -S is required.");
            return -1;
        }

        // Preemptive SJF is only implemented for a single core (Task 1)
        if (alg == 4 && cores > 1) {
            System.out.println("\nError: Preemptive SJF (-S 4) only runs on a single core. Use -C 1 or leave -C off.");
            return -1;
        }

        quantum = q;
        CORES = cores;
        return alg;
    }

    // True if there is a parameter at args[index] (something that is not the next -S / -C flag)
    static boolean hasValue(String[] args, int index) {
        return index < args.length && !args[index].equals("-S") && !args[index].equals("-C");
    }

    // Only exact integers from min to max are accepted (rejects "", " 2", "+1", "01", "2.0", etc.)
    // Returns -1 if not valid
    static int parseExact(String s, int min, int max) {
        for (int v = min; v <= max; v++) {
            if (s.equals(String.valueOf(v))) {
                return v;
            }
        }
        return -1;
    }

    // Print argument options
    static void printUsage() {
        System.out.println("\nPlease Choose: -S <algorithm #> [-C <cores>]\n" +
                " -S 1           -> First-come, first-served\n" +
                " -S 2 <quantum> -> Round-robin, <quantum> is 2-10\n" +
                " -S 3           -> Non-preemptive shortest job first\n" +
                " -S 4           -> Preemptive shortest job first (single core only)\n" +
                " -C <cores>     -> Number of cores, 1-4 (optional, defaults to 1)");
    }

    public static void main(String[] args) throws InterruptedException {

        // Validate arguments before any tasks are created or any threads start
        algorithm = checkArg(args);
        if (algorithm < 0) {
            printUsage();
            return;
        }

        boolean MULTICORE = CORES > 1; // 1 core = Task 1 dispatcher, 2-4 cores = Task 2 dispatchers

        Random rand = new Random();
        int T = rand.nextInt(25) + 1;

        if (MULTICORE) {
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

        String algo = ALGO_NAMES[algorithm - 1];
        System.out.println("CPU Cores: " + CORES);
        if (algorithm == 2) {
            System.out.println("Time Quantum: " + quantum);
        }

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
        for (taskThread t : arrivedNow) { // Implemented another loop because I kept crashing
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