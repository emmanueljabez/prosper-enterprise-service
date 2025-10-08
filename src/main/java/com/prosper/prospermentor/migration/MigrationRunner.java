package com.prosper.prospermentor.migration;

import com.prosper.prospermentor.service.MigrationService;
import com.prosper.prospermentor.service.MongoDataReaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Command line runner to execute migration when the application starts
 */
//@Component
//@ConditionalOnProperty(name = "migration.auto-run", havingValue = "true")
public class MigrationRunner implements CommandLineRunner {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private MongoDataReaderService mongoDataReaderService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 STARTING MONGODB TO SUPABASE MIGRATION");
        System.out.println("=" + "=".repeat(60));

        try {
            // Check if collection files exist
            if (!mongoDataReaderService.validateCollectionFiles()) {
                System.err.println("❌ Collection files validation failed");
                return;
            }

            // Get data statistics
            var stats = mongoDataReaderService.getDataStatistics();
            System.out.println("📊 Data Statistics:");
            stats.forEach((key, value) -> 
                System.out.printf("   %s: %s%n", key, value));

            // Execute migration
            System.out.println("\n🔄 Executing migration...");
            var result = migrationService.executeMigration();

            System.out.printf("\n✅ Migration completed with status: %s%n", result.status());
            
            if (!result.log().isEmpty()) {
                System.out.println("\n📝 Migration logs:");
                result.log().forEach(System.out::println);
            }

            if (result.validation() != null) {
                System.out.println("\n🔍 Validation results:");
                System.out.println("Entity counts: " + result.validation().entityCounts());
                System.out.println("Mapping counts: " + result.validation().mappingCounts());
                System.out.println("Integrity check: " + result.validation().integrityCheck());
            }

            System.out.println("\n🎉 MIGRATION COMPLETED SUCCESSFULLY!");
            System.out.println("=" + "=".repeat(60));

        } catch (Exception e) {
            System.err.println("❌ Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
