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
    boolean isFirstReading = true;
    char expectedTimeSlotId = 'A';


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
        String line;
        String parts[];
        String delimiter = " ";
        while(lineCount < NUMBER_OF_SENSORS) {
            line = dataFile.nextLine();
            parts = line.split(delimiter);
            for (int index = 0; index < 2; index++)
            {
                sensors[lineCount][index] = Integer.parseInt(parts[index]);
            }
            lineCount++;
        }

        // At this point, your logger has been set up and your datafile is ready to read
    }


    /**
     * Get the next valid entry from the file
     *
     * @return the ReadingSet for the next valid entry
     */
    public ReadingSet getNext() throws NoMoreData
    {
        char timeSlotId = ' ';
        int data[] = {0, 0, 0};
        String line;
        String parts[];
        String delimiter = " ";

        // check for eof
        if (!dataFile.hasNextLine())
        {
            throw new NoMoreData();
        } else {
            line = dataFile.nextLine();
            parts = line.split(delimiter);
            timeSlotId = parts[0].charAt(0);

            // check for either missing data or too much data
            if(parts.length < 4) {
                logger.severe("Record is missing data");
                close();
                getNext();
            } else if (parts.length >= 5) {
                logger.severe("Record has too much data");
                close();
                getNext();
            }

            // check for bad time slot id
                if (isFirstReading) {
                isFirstReading = false;

            }else if (timeSlotId < 'A' || timeSlotId >= 'P'){
                timeSlotId = expectedTimeSlotId;
                logger.info("Time Slot ID is out of range, replaced with expected ID");
            }else if (timeSlotId != expectedTimeSlotId) {

                int distanceOff = calcNumOfPositionsOff(timeSlotId, expectedTimeSlotId);

                //Check if one line was missed.
                if (distanceOff == 1)
                {
                        //Incorrect TimeSlotID is set to the expected ID
                        //TODO return the values of the last sensor reading
                        timeSlotId = expectedTimeSlotId;
                        logger.info("Missing reading. Returning all values of the last reading with expected ID.");
                }else {
                    logger.severe("Time Slot ID is off by more than 1 position. Going forward as if entry is correct.");
                }

            }

            // read in data and check for out of range
            for (int index = 0; index < NUMBER_OF_SENSORS; index++)
            {
                data[index] = Integer.parseInt(parts[index + 1]);
                if ((data[index] > sensors[index][1]) && (data[index] < (sensors[index][1] * 1.5))) {
                    data[index] = sensors[index][1];
                    logger.info("Reading value is too high.  Setting to max value");
                } else if (data[index] < sensors[index][0])                 {
                    data[index] = sensors[index][0];
                    logger.info("Reading value is too low.  Setting to min value");
                }
                while ((data[index] >= (sensors[index][1] * 1.5)) && dataFile.hasNext()) {
                    data[index] = sensors[index][1];
                    logger.severe("Reading value is at least 150% of max value.  Setting to max value");
                    line = dataFile.nextLine();
                    timeSlotId = line.charAt(0);
                    parts = line.split(" ");
                }
            }
        }
        //determine the next expected Time Slot ID
        expectedTimeSlotId = calcExpectedTimeSlotId(timeSlotId);
        // return reading set with data and time slot id
        ReadingSet readingSet = new ReadingSet(timeSlotId, data);
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
    public char calcExpectedTimeSlotId(char currId)
    {
        if (currId == 'O')
        {
            return 'A';
        } else {
            int asciiOfCurrId = currId;
            asciiOfCurrId++;
            return (char) asciiOfCurrId;
        }
    }

    

    public int calcNumOfPositionsOff(int currId, int expectedValue)
    {
        int numPositionsOff = Character.getNumericValue(currId) -
                Character.getNumericValue(expectedValue);
        return numPositionsOff;
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
}
