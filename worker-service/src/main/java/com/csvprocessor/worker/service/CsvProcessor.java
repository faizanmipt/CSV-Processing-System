package com.csvprocessor.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * CSV File Processor Service for streaming file processing operations.
 * 
 * This service implements a production-grade CSV file processor that:
 * - Performs streaming read operations for memory efficiency
 * - Processes CSV records and adds email detection flags
 * - Uses batch buffered writes for optimal I/O performance
 * - Provides reactive return types for non-blocking integration
 * 
 * Architecture:
 * - Memory efficient: O(1) memory usage regardless of file size
 * - Streaming architecture: Processes files line-by-line
 * - Batch writes: Accumulates records before writing for I/O efficiency
 * - Async I/O: Uses Java NIO AsynchronousFileChannel for non-blocking writes
 * 
 * Processing Pipeline:
 * 1. Stream read input file line-by-line (memory efficient)
 * 2. Process each line to detect email addresses
 * 3. Buffer processed lines into batches
 * 4. Write batches asynchronously to output file
 * 5. Write remaining records in final batch on completion
 * 
 * @author Faizan Nabi
 * @version 1.0.0
 * @since 2026
 */
@Service
public class CsvProcessor {

    /** Logger instance for this service */
    private static final Logger logger = LoggerFactory.getLogger(CsvProcessor.class);

    /** Directory path for storing processed output files */
    @Value("${file.processed.dir}")
    private String processedDir;

    /** Number of records to accumulate before triggering a batch write */
    @Value("${worker.batch.size:5000}")
    private int batchSize;

    /** Regular expression pattern for validating email addresses */
    @Value("${worker.email.pattern:[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}}")
    private String emailPatternString;

    /** Compiled email pattern for efficient validation */
    private Pattern EMAIL_PATTERN;

    /**
     * Initializes the email pattern after bean construction.
     * Called by Spring after dependency injection is complete.
     */
    @PostConstruct
    public void init() {
        EMAIL_PATTERN = Pattern.compile(emailPatternString);
        logger.info("CSV Processor initialized - batchSize={}", batchSize);
    }

    /**
     * Determines whether a CSV row contains a valid email address.
     * 
     * This method scans all fields in a CSV row to detect email patterns.
     * Email validation is performed on unquoted and unescaped field values.
     * 
     * @param line The CSV row to analyze
     * @return true if the row contains a valid email address, false otherwise
     */
    private boolean hasEmailInRow(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String[] fields = line.split(",");
        for (String field : fields) {
            String cleanedField = field.trim();
            if (cleanedField.startsWith("\"") && cleanedField.endsWith("\"")) {
                cleanedField = cleanedField.substring(1, cleanedField.length() - 1);
            }
            if (EMAIL_PATTERN.matcher(cleanedField).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processes a single CSV line by appending an email detection flag.
     * 
     * @param line The original CSV line
     * @return The processed line with appended has_email boolean flag
     */
    private String processLine(String line) {
        boolean hasEmail = hasEmailInRow(line);
        return line + "," + hasEmail;
    }

    /**
     * Processes a CSV file using streaming read and batch buffered write.
     * 
     * This method is the main entry point for file processing. It:
     * - Reads the input file using buffered streaming (memory efficient)
     * - Processes each line to detect email addresses
     * - Writes processed lines to the output file in batches
     * - Returns a Mono that completes when processing finishes
     * 
     * Processing guarantees:
     * - The output file has the same name as the input with a .csv extension
     * - Each processed line includes an additional "has_email" column
     * - The header row is preserved with the new column appended
     * 
     * @param rawFilePath The absolute path to the input CSV file
     * @param jobId The job identifier for generating output filename
     * @return Mono containing the path to the processed output file
     */
    public Mono<String> processFile(String rawFilePath, String jobId) {
        Path inputPath = Paths.get(rawFilePath);
        Path outputPath = Paths.get(processedDir).resolve(jobId + ".csv");

        logger.info("Initiating CSV file processing - input={}, output={}", 
                inputPath, outputPath);
        logger.info("Batch size configured: {}", batchSize);

        return Mono.fromCallable(() -> Files.createDirectories(outputPath.getParent()))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(dir -> processFileStreaming(inputPath, outputPath))
            .doOnSuccess(result -> logger.info("CSV processing completed - output={}", result))
            .doOnError(error -> logger.error("CSV processing failed - {}", error.getMessage()));
    }

    /**
     * Implements the streaming read and batch buffered write algorithm.
     * 
     * Algorithm steps:
     * 1. Open async file channel for output
     * 2. Read and write header row first
     * 3. Stream read remaining lines
     * 4. Process each line and accumulate in buffer
     * 5. Write buffer when batch size is reached
     * 6. Write final partial batch on stream completion
     * 
     * @param inputPath The path to the input CSV file
     * @param outputPath The path to the output CSV file
     * @return Mono containing the output file path
     */
    private Mono<String> processFileStreaming(Path inputPath, Path outputPath) {
        return Mono.fromCallable(() -> {
            // Open asynchronous file channel for non-blocking write operations
            AsynchronousFileChannel outputChannel = AsynchronousFileChannel.open(
                outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            // Process file using streaming read with batch buffered write
            try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
                
                // Step 1: Read and write header row
                String header = reader.readLine();
                if (header != null) {
                    String processedHeader = header + ",has_email";
                    ByteBuffer headerBuffer = ByteBuffer.wrap((processedHeader + "\n").getBytes());
                    writeAsync(outputChannel, headerBuffer).get();
                }

                // Step 2: Stream read, process, and batch write data rows
                String line;
                List<String> batch = new ArrayList<>(batchSize);
                int lineNumber = 0;
                int totalBytesWritten = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    
                    if (line.trim().isEmpty()) continue;
                    
                    // Process line and add to batch buffer
                    String processedLine = processLine(line);
                    batch.add(processedLine);

                    // Write batch when buffer threshold is reached
                    if (batch.size() >= batchSize) {
                        String batchData = String.join("\n", batch) + "\n";
                        ByteBuffer batchBuffer = ByteBuffer.wrap(batchData.getBytes());
                        int bytesWritten = writeAsync(outputChannel, batchBuffer).get();
                        totalBytesWritten += bytesWritten;
                        batch.clear();
                        logger.debug("Batch write completed - records={}, bytes={}", 
                                batchSize, bytesWritten);
                    }
                }

                // Step 3: Write any remaining records in final batch
                if (!batch.isEmpty()) {
                    String batchData = String.join("\n", batch) + "\n";
                    ByteBuffer batchBuffer = ByteBuffer.wrap(batchData.getBytes());
                    int bytesWritten = writeAsync(outputChannel, batchBuffer).get();
                    totalBytesWritten += bytesWritten;
                    batch.clear();
                    logger.debug("Final batch write completed - records={}, bytes={}", 
                            batch.size(), bytesWritten);
                }

                // Close channel and return result
                outputChannel.close();
                logger.info("Processing complete - linesProcessed={}, bytesWritten={}, output={}", 
                        lineNumber, totalBytesWritten, outputPath);
                return outputPath.toString();

            } finally {
                // Ensure channel is closed even on exception
                try {
                    outputChannel.close();
                } catch (IOException e) {
                    logger.warn("Failed to close output file channel", e);
                }
            }
        });
    }

    /**
     * Performs asynchronous file write using AsynchronousFileChannel.
     * 
     * This method wraps the NIO asynchronous write operation in a
     * CompletableFuture for integration with reactive streams.
     * 
     * @param channel The asynchronous file channel to write to
     * @param buffer The byte buffer containing data to write
     * @return CompletableFuture containing the number of bytes written
     */
    private CompletableFuture<Integer> writeAsync(AsynchronousFileChannel channel, ByteBuffer buffer) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        
        channel.write(buffer, buffer.position(), buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                future.complete(result);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
}
