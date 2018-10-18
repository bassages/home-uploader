package nl.homesensors.smartmeter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Reads data from a serial device which is connected to the P1 port of a Smart Meter.
 * The data will be posted to home-server.
 *
 * Needs the "cu" command to be installed on the host OS.
 */
@Component
public class SmartMeterReaderNative {

    private static final Logger LOG = LoggerFactory.getLogger(SmartMeterReaderNative.class);

    @Value("${smart-meter-serial-port-path:#{null}}")
    private String smartMeterSerialPortPath;
    @Value("${smart-meter-serial-port-baudrate:#{null}}")
    private String smartMeterSerialPortBaudRate;
    @Value("${smart-meter-serial-port-parity:#{null}}")
    private String smartMeterSerialPortParity;

    private final MessageBuffer messageBuffer;

    public SmartMeterReaderNative(final MessageBuffer messageBuffer) {
        this.messageBuffer = messageBuffer;
    }

    @Async
    public void run() {
        if (smartMeterSerialPortPath == null || smartMeterSerialPortBaudRate == null || smartMeterSerialPortParity == null) {
            LOG.warn("Not started SmartMeterReader, because the configuration for it is not defined.");
        } else {
            LOG.info("Start SmartMeterReader");
            connectAndListenForData();
        }
    }

    private void connectAndListenForData() {
        try {
            final String command = "cu -l " + smartMeterSerialPortPath + " --speed " + smartMeterSerialPortBaudRate + " --parity=" + smartMeterSerialPortParity + " -E q";

            final Process process = Runtime.getRuntime().exec(command);

            final Thread ioThread = new Thread(() -> {
                handleInputStreamLines(process.getInputStream(), messageBuffer::addLine);
                handleInputStreamLines(process.getErrorStream(), LOG::error);
            });
            ioThread.start();

            final int exitValue = process.waitFor();
            if (exitValue != 0) {
                LOG.warn("Unexpected exit value from command. Exit value=[{}]", exitValue);
            }

        } catch (final InterruptedException  e) {
            LOG.error("An unexpected InterruptedException occurred.", e);
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            LOG.error("An unexpected IOException occurred.", e);
        }
    }

    private void handleInputStreamLines(final InputStream inputStream, final Consumer<String> lineHandler) {
        try (final var inputStreamReader = new InputStreamReader(inputStream)) {
            final LineIterator it = IOUtils.lineIterator(inputStreamReader);
            while (it.hasNext()) {
                lineHandler.accept(it.nextLine());
            }
        } catch (final IOException e) {
            LOG.error("InputStream failure", e);
        }
    }
}