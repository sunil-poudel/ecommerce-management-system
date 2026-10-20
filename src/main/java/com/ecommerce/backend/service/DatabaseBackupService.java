package com.ecommerce.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;

@Service
public class DatabaseBackupService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseBackupService.class);

    @Value("${backup.enabled:false}")
    private boolean backupEnabled;

    @Value("${backup.dir:./db_backups}")
    private String backupDirPath;

    @Value("${spring.datasource.username:root}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    // Run every day at 2:00 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void backupDatabase() {
        if (backupEnabled) {
            performBackup();
        }
    }
    
    // Also run a backup on startup only if enabled
    @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
    public void initialBackup() {
        if (backupEnabled) {
            performBackup();
        }
    }

    private void performBackup() {
        try {
            File backupDir = new File(backupDirPath);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String dumpCommand = isCommandAvailable("mariadb-dump") ? "mariadb-dump"
                    : (isCommandAvailable("mysqldump") ? "mysqldump" : null);

            if (dumpCommand == null) {
                logger.warn("Neither mariadb-dump nor mysqldump was found in system PATH. Skipping automated database backup.");
                return;
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = "ecommerce_db_backup_" + timestamp + ".sql";
            File backupFile = new File(backupDir, fileName);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    dumpCommand, "-u", dbUser, "-p" + dbPassword, "ecommerce_db"
            );
            
            // Redirect output directly to file
            processBuilder.redirectOutput(backupFile);
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Database backup completed successfully: " + backupFile.getAbsolutePath());
                cleanupOldBackups(backupDir);
            } else {
                logger.error("Database backup failed with exit code: " + exitCode);
                if (backupFile.exists()) {
                    backupFile.delete();
                }
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Exception during database backup: ", e);
            Thread.currentThread().interrupt();
        }
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process process = new ProcessBuilder("which", cmd).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void cleanupOldBackups(File backupDir) {
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("ecommerce_db_backup_") && name.endsWith(".sql"));
        if (files != null && files.length > 7) { // Keep last 7 backups
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - 7; i++) {
                if (files[i].delete()) {
                    logger.info("Deleted old backup: " + files[i].getName());
                }
            }
        }
    }
}
