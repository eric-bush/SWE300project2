import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * This class parses a sensor log file.  In addition to returning the ReadingSets that it finds, it logs
 * any errors it saw in the file.  Those logs will be a file whose title and path match the file being
 * read with ".log" added at the end of the file title.
 */
public class SensorReadingsParser
{
    static final int NUMBER_OF_SENSORS = 3;
    static final char FIRST_TIME_SLOT_ID = 'A';
    static final int READINGS_PER_GROUP = 15;
    private static final Logger logger =
            Logger.getLogger(SensorReadingsParser.class.getName());
    private final Scanner dataFile;

    private int[][] sensors = { { 0, 0 }, { 0, 0 }, { 0, 0 } };
    private char expectedTimeSlotId = FIRST_TIME_SLOT_ID;

    private int[] previousSensorData = {0, 0, 0};
    private boolean isFirstReading = true;
    private int[] sensorReadings = {0, 0, 0};
    private char timeSlotId;
    private char prevTimeSlotID;
    private boolean isMissingOnePreviousReading;




    /**
     * Create an object that can read our sensor data files
     *
     * @param fileTitle the title of the file this object should read
     * @throws IOException if it can't find the file or create an appropriate log file
     */
    public SensorReadingsParser(String fileTitle) throws IOException
    {
        FileHandler fileHandler = new FileHandler(fileTitle + ".log");

        //Assigning handlers to LOGGER object
        logger.addHandler(fileHandler);

        fileHandler.setLevel(Level.INFO);
        logger.setLevel(Level.ALL);

        dataFile = new Scanner(new File(fileTitle));

        int lineCount = 0;
        String lineInFile;
        String[] sensorInput;
        String delimiter = " ";
        while(lineCount < NUMBER_OF_SENSORS) {
            lineInFile = dataFile.nextLine();
            sensorInput = lineInFile.split(delimiter);
            for (int index = 0; index < 2; index++)
            {
                sensors[lineCount][index] = Integer.parseInt(sensorInput[index]);
            }
            lineCount++;
        }

        // At this point, your logger has been set up and your datafile is ready to read
    }
    String lineInFile;

    /**
     * Get the next valid entry from the file
     *
     * @return the ReadingSet for the next valid entry
     */
    public ReadingSet getNext() throws NoMoreData {
        //String lineInFile;
        String[] parts;
        String delimiter = " ";

        // check for eof if previous line was not skipped
        if (!dataFile.hasNextLine() && !isMissingOnePreviousReading) {
            throw new NoMoreData();
        } else {
            if (!isMissingOnePreviousReading) {
                lineInFile = dataFile.nextLine();
            }else {
                isMissingOnePreviousReading = false;
            }

            parts = lineInFile.split(delimiter);
            timeSlotId = parts[0].charAt(0);
            boolean isValidLength = true;
            boolean isValidTimeID = timeIDCheck(parts);
            boolean isSensorDataValid = false;

            if ((isValidLength = dataAmountCheck(parts.length)) && (isSensorDataValid = checkDigits(parts))
                    && isValidTimeID) {
                // read in data and check for out of range
                for (int index = 0; index < NUMBER_OF_SENSORS; index++) {
                    sensorReadings[index] = Integer.parseInt(parts[index + 1]);
                    if (isOutOfRangeCheck(index)) {
                        lineInFile = dataFile.nextLine();
                        timeSlotId = lineInFile.charAt(0);
                        parts = lineInFile.split(" ");
                    }
                }
                // check if data is matching
                checkMatching(sensorReadings);

                previousSensorData = sensorReadings;
                prevTimeSlotId = timeSlotId;
            }
        }
        //determine the next expected Time Slot ID
        expectedTimeSlotId = calcExpectedTimeSlotId(timeSlotId);
        // return reading set with data and time slot id
        ReadingSet readingSet = new ReadingSet(timeSlotId, sensorReadings);
        return readingSet;
    }

    /**
     * Return the min legal value for a given sensor
     *
     * @param index the offset of the sensor in the data section of each record
     * @return the min legal value
     */
    public int getMin(int index)
    {
        return sensors[index][0];
    }

    /**
     * Return the max legal value for a given sensor
     *
     * @param index the offset of the sensor in the data section of each record
     * @return the max legal value
     */
    public int getMax(int index)
    {
        return sensors[index][1];
    }

    /**
     * Close this reader.  Will close the log file
     */
    public void close()
    {
        LogManager.getLogManager().reset();
    }

    static class NoMoreData extends Exception
    {

    }

    private boolean dataAmountCheck(int length) throws NoMoreData {
        if(length < 4) {
            logger.severe("Record is missing data");
            close();
            getNext();
            return false;
        } else if (length > 4) {
            logger.severe("Record has too much data");
            close();
            getNext();
            return false;
        }
        return true;
    }

    private boolean timeIDCheck(String[] parts) throws NoMoreData {
        if (isFirstReading) {
            isFirstReading = false;
        } else if (timeSlotId < 'A' || timeSlotId > 'O'){
            timeSlotId = expectedTimeSlotId;
            System.out.println("Time Slot ID is out of range");
            logger.severe("Time Slot ID is out of range, replaced with expected ID");
            getNext();
            return false;
        } else if (parts[0].length() > 1) {
            timeSlotId = expectedTimeSlotId;
            System.out.println("Time Slot ID is too long");
            logger.severe("Time Slot ID is too long");
            getNext();
            return false;
        } else if (timeSlotId != expectedTimeSlotId) {
            int distanceOff = calcNumOfPositionsOff(timeSlotId, expectedTimeSlotId);
            //Check if one line was missed.
            if (distanceOff == 1) {
                //Incorrect TimeSlotID is set to the expected ID
                isMissingOnePreviousReading = true;
                timeSlotId = expectedTimeSlotId;
                sensorReadings = previousSensorData;
                logger.info("Missing reading. Returning all values of the last reading with expected ID.");
                //getNext(); removed
                return false;
            } else if (distanceOff > 1) {
                logger.severe("Time Slot ID is off by more than 1 position. Going forward as if entry is correct.");
            }
        }
        return true;
    }

    
    public char calcExpectedTimeSlotId(char currId)
    {
        if (currId == 'O')
        {
            return 'A';
        } else  {
            int asciiOfCurrId = currId;
            asciiOfCurrId++;
            return (char) asciiOfCurrId;
        }
    }

    public int calcNumOfPositionsOff(int asciiOfCurrId, int asciiOfExpectedValue)
    {
        return asciiOfCurrId - asciiOfExpectedValue;
    }

    private boolean checkDigits(String parts[]) throws NoMoreData {
        for (int index = 0; index < NUMBER_OF_SENSORS; index++) {
            for (int i = 0; i < parts[index + 1].length(); i++) {
                if (Character.isLetter(parts[index + 1].charAt(i))) {
                    // reruns line with C in sensor 1 data slot
                    logger.severe("Sensor reading is not a number");
                    getNext();
                    return false;
                }
            }
        }
        return true;
    }

    private void checkMatching(int[] data) {
        if (data[0] == data[1]) {
            logger.info("Sensor 1 and 2 are matching");
        } else if (data[0] == data[2]) {
            logger.info("Sensor 1 and 3 are matching");
        } else if (data[1] == data[2]) {
            logger.info("Sensor 2 and 3 are matching");
        }
    }

    private boolean isOutOfRangeCheck(int index) {
        int min = getMin(index);
        int max = getMax(index);

        if ((sensorReadings[index] > max) && (sensorReadings[index] < (max * 1.5))) {
            sensorReadings[index] = sensors[index][1];
            logger.info("Reading value is too high.  Setting to max value");
        } else if (sensorReadings[index] < min) {
            sensorReadings[index] = sensors[index][0];
            logger.info("Reading value is too low.  Setting to min value");
        }
        while ((sensorReadings[index] >= (max * 1.5)) && dataFile.hasNext()) {
            sensorReadings[index] = max;
            logger.severe("Reading value is at least 150% of max value.  Setting to max value");
            return true;
        }
        return false;
    }
}
