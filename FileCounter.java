import java.io.File;
import java.util.concurrent.*;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class FileCounter {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Введення користувачем даних
        System.out.println("Введіть шлях до директорії:");
        String directoryPath = scanner.nextLine();
        System.out.println("Введіть розширення файлів для пошуку (наприклад, .pdf):");
        String fileExtension = scanner.nextLine();

        // Використання Fork/Join Framework із підходом Work Stealing
        long startTimeStealing = System.nanoTime();
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        FileCountTask task = new FileCountTask(new File(directoryPath), fileExtension);
        int resultStealing = forkJoinPool.invoke(task);
        long endTimeStealing = System.nanoTime();

        // Використання ExecutorService для підходу Work Dealing
        long startTimeDealing = System.nanoTime();
        int resultDealing = countFilesWithWorkDealing(directoryPath, fileExtension);
        long endTimeDealing = System.nanoTime();

        // Виведення результатів
        System.out.println("Кількість файлів за допомогою Work Stealing: " + resultStealing);
        System.out.println("Час виконання (Work Stealing): " + (endTimeStealing - startTimeStealing) / 1_000_000 + " мс");

        System.out.println("Кількість файлів за допомогою Work Dealing: " + resultDealing);
        System.out.println("Час виконання (Work Dealing): " + (endTimeDealing - startTimeDealing) / 1_000_000 + " мс");
    }

    // Завдання Fork/Join для підрахунку файлів
    static class FileCountTask extends RecursiveTask<Integer> {
        private final File directory;
        private final String fileExtension;

        FileCountTask(File directory, String fileExtension) {
            this.directory = directory;
            this.fileExtension = fileExtension;
        }

        @Override
        protected Integer compute() {
            File[] files = directory.listFiles();
            if (files == null) return 0;

            List<FileCountTask> tasks = new ArrayList<>();
            int count = 0;

            for (File file : files) {
                if (file.isDirectory()) {
                    // Якщо це директорія, створюємо нове завдання
                    FileCountTask task = new FileCountTask(file, fileExtension);
                    tasks.add(task);
                    task.fork(); // Асинхронне виконання підзадачі
                } else if (file.getName().endsWith(fileExtension)) {
                    // Якщо це файл з потрібним розширенням, збільшуємо лічильник
                    count++;
                }
            }

            // Очікуємо завершення підзадач і додаємо їх результати
            for (FileCountTask task : tasks) {
                count += task.join();
            }

            return count;
        }
    }

    // Реалізація Work Dealing за допомогою ExecutorService
    private static int countFilesWithWorkDealing(String directoryPath, String fileExtension) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        BlockingQueue<File> directories = new LinkedBlockingQueue<>();
        directories.add(new File(directoryPath));
        List<Future<Integer>> futures = new ArrayList<>();

        while (!directories.isEmpty()) {
            File dir = directories.poll();
            if (dir == null) continue;

            futures.add(executor.submit(() -> {
                int count = 0;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            // Додаємо піддиректорії до черги
                            directories.add(file);
                        } else if (file.getName().endsWith(fileExtension)) {
                            // Якщо це файл з потрібним розширенням, збільшуємо лічильник
                            count++;
                        }
                    }
                }
                return count;
            }));
        }

        int totalCount = 0;
        for (Future<Integer> future : futures) {
            try {
                totalCount += future.get(); // Очікуємо результат виконання завдання
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();
        return totalCount;
    }
}
