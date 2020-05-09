package hello; 

import java.io.File;  
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.opencsv.CSVWriter;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class reports_code {

	/***
	 * Notes on how to use this program AND/OR additions necessary: 
	 * 1. Until a database/postgres table has been created to indicate the active/operating
	 * dopravci, the Map list of valid operators and their corresponding CIS IDs
	 * must be updated manually The same is true for updating the IF-ELSE statement
	 * distinguishing between vlaky and autobusy operators  
	 * 2. All SQL queries are automatically including the first day of the
	 * previous month and up to but not including first day of current month (unless
	 * other arguments are provided) 
	 * 3. If arguments are provided, then they must
	 * have the following format and order: 
	 * 		first argument (start_date) = YYYY-MM-DD
	 * 		second argument (end_date) = YYYY-MM-DD 
	 * 		third argument (file_ending) = MM_YYYY 
	 * For example, if you want to create a report for the month of June
	 * 2019 using manually input arguments, use the following arguments: 
	 * 		first argument = 2019-06-01 (first day of month) 
	 * 		second argument = 2019-06-30 (last day of month) 
	 * 		third argument = 06_2019
	 */

	private static final DateFormat sdf = new SimpleDateFormat("yyyy-MM");

	public static void main(String[] args) throws Exception {



		// Declaring and initializing variables with previous month information
		LocalDate localDate = LocalDate.now();
		LocalDate earlier = localDate.minusMonths(1);
		String formattedDate = earlier.format(DateTimeFormatter.ofPattern("YYYY-MM"));
		String start_date = "" + formattedDate + "-01";

		// Getting first day of current month
		Date date = new Date();
		String end_date = "" + sdf.format(date) + "-01";

		// Adjust String date for printing correct time frame
		LocalDate minus_adjustment = LocalDate.parse(end_date);
		LocalDate minus = minus_adjustment.minusDays(1);
		String end_date_string = "" + minus;

		// Getting report period
		String report_formattedDate = earlier.format(DateTimeFormatter.ofPattern("_MM_YYYY"));
		String report_period = report_formattedDate;

		// If 3 arguments are provided then variable values change to the input values
		if (args.length == 3) {
			start_date = args[0].trim();
			end_date_string = args[1].trim();
			// SQL code requires end_date input +1 days past desired period, since output is
			// up to but not including end_date
			LocalDate adjusted = LocalDate.parse(end_date_string);
			LocalDate later = adjusted.plusDays(1);
			end_date = "" + later;
			report_period = "_" + args[2].trim();
		}

		System.out.println("Creating cis reports for period from " + start_date + " to " + end_date_string + " . . . \n");

		// Loading the PostgreSQL driver into your Java program.
		Class.forName("org.postgresql.Driver");

		// Host
		String host = System.getenv("DATABASE_HOST");

		// Port: Obtain the port from within PgAdmin
		String port = System.getenv("DATABASE_PORT");

		// Database
		String database = System.getenv("DATABASE");

		// Form the connection URL.
		String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

		// Open the connection to the PostgreSQL server.
		Properties props = new Properties();
		props.setProperty("user", System.getenv("DATABASE_USER"));
		props.setProperty("password", System.getenv("DATABASE_PASSWORD")); // Need to secure password
		props.setProperty("ssl", "false");
		Connection connection = DriverManager.getConnection(url, props);

		// Dictionary Map of operators and their corresponding CIS operator IDs
		// MUST ADD OR REMOVE OPERATORS WHEN NECESSARY UNTIL APPROPRIATE TABLE HAS BEEN
		// CREATED
		Map<String, Integer> operators = new HashMap<String, Integer>();
		operators.put("cis_karelmudroch", 785);
		operators.put("cis_dailybus", 1687);
		operators.put("cis_arrivavychodnicechy", 1154);
		operators.put("cis_arrivavlaky", 1106);
		operators.put("cis_arrivamorava", 1147);
		operators.put("cis_arrivacity", 2253);

		// declaring and initializing string variables for csv files

		String file_name_dopravci = "";
		String file_name_jizdenky = "";
		String file_name_verze = "";

		// text file that keeps list of which operators need to be sent report via email
		ArrayList<String> upload_list = new ArrayList<String>(); 

		// For loop to iterate through operators and create corresponding report files
		for (Map.Entry<String, Integer> entry : operators.entrySet()) {

			/*** Verze CIS Report Code ***/

			// Creating CSV
			file_name_verze = "verze.csv";
			CSVWriter writer_verze = new CSVWriter(new FileWriter(file_name_verze), CSVWriter.DEFAULT_SEPARATOR,
					CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, ";\r\n");

			// Execute an SQL SELECT statement on the PostgreSQL server.
			Statement select_verze = connection.createStatement();
			// Query to get verze data
			ResultSet results_verze = select_verze
					.executeQuery("SELECT\r\n" + "  '1.0'                    AS \"cislo_verze\",\r\n"
							+ "  to_char(NOW(), 'DDMMYY') AS \"datum_exportu\",\r\n"
							+ "  'Bileto System'          AS \"jmeno\";");

			// List of results and adding results to CSV file
			List<String[]> data_verze = new ArrayList<String[]>();
			while (results_verze.next()) {
				data_verze.add(new String[] { results_verze.getString(1), results_verze.getString(2),
						results_verze.getString(3) });
			}
			writer_verze.writeAll(data_verze);
			// closing
			writer_verze.close();
			select_verze.close();

			/*** Dopravci CIS Report Code ***/

			// Creating CSV
			file_name_dopravci = "dopravci.csv";
			CSVWriter writer_dopravci = new CSVWriter(new FileWriter(file_name_dopravci), CSVWriter.DEFAULT_SEPARATOR,
					CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, ";\r\n");

			// Execute an SQL SELECT statement on the PostgreSQL server.
			Statement select_dopravci = connection.createStatement();
			// Query to get dopravci data
			ResultSet results_dopravci = select_dopravci.executeQuery(
					"SELECT * FROM cis_dopravci(" + entry.getValue() + ",'" + start_date + "','" + end_date + "')");

			// List of results and adding results to CSV file
			List<String[]> data_dopravci = new ArrayList<String[]>();
			while (results_dopravci.next()) {
				data_dopravci.add(new String[] { results_dopravci.getString(1), results_dopravci.getString(2),
						results_dopravci.getString(3), "", results_dopravci.getString(5), results_dopravci.getString(6),
						results_dopravci.getString(7), results_dopravci.getString(8) });

			}
			writer_dopravci.writeAll(data_dopravci);
			// closing
			writer_dopravci.close();
			select_dopravci.close();

			// Creating jizdenky file
			file_name_jizdenky = "jizdenky.csv";

			// If statement distinguishing between those that operate autobusy and those
			// that operate vlaky
			// Currently hard-coded, but with the right table, could pull info from database
			// and not require manual updating
			if (!entry.getKey().equals("cis_arrivavlaky")) {

				/*** Jizdenky autobusy CIS Report Code ***/

				// Creating CSV
				CSVWriter writer_auto = new CSVWriter(new FileWriter(file_name_jizdenky), CSVWriter.DEFAULT_SEPARATOR,
						CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, ";\r\n");

				// Execute an SQL SELECT statement on the PostgreSQL server.
				Statement select_auto = connection.createStatement();
				// Query to get autobusy data
				ResultSet results_auto = select_auto.executeQuery("SELECT * FROM cis_bus_jizdenky(" + entry.getValue()
				+ ",'" + start_date + "','" + end_date + "')");

				// List of results and adding results to CSV file
				List<String[]> data_auto = new ArrayList<String[]>();
				while (results_auto.next()) {
					data_auto.add(new String[] { results_auto.getString(1), results_auto.getString(2),
							results_auto.getString(3), results_auto.getString(4), results_auto.getString(5),
							results_auto.getString(6), results_auto.getString(7), results_auto.getString(8),
							results_auto.getString(9), results_auto.getString(10), results_auto.getString(11),
							"" + results_auto.getString(12) + "", "" + results_auto.getString(13) + "",
							results_auto.getString(14), results_auto.getString(15), results_auto.getString(16),
							results_auto.getString(17), "", results_auto.getString(19), results_auto.getString(20) });
				}
				writer_auto.writeAll(data_auto);
				// closing
				writer_auto.close();
				select_auto.close();

			}

			else {

				/*** Jizdenky vlaky CIS Report Code ***/

				// Creating CSV
				CSVWriter writer_vlaky = new CSVWriter(new FileWriter(file_name_jizdenky), CSVWriter.DEFAULT_SEPARATOR,
						CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, ";\r\n");
				// Execute an SQL SELECT statement on the PostgreSQL server.
				Statement select_vlaky = connection.createStatement();
				// Query to get vlaky data
				ResultSet results_vlaky = select_vlaky.executeQuery("SELECT * FROM cis_train_jizdenky("
						+ entry.getValue() + ",'" + start_date + "','" + end_date + "')");

				// List of results and adding results to CSV file
				List<String[]> data_vlaky = new ArrayList<String[]>();
				while (results_vlaky.next()) {
					data_vlaky.add(new String[] { results_vlaky.getString(1), results_vlaky.getString(2),
							results_vlaky.getString(3), results_vlaky.getString(4), results_vlaky.getString(5),
							results_vlaky.getString(6), results_vlaky.getString(7), results_vlaky.getString(8),
							results_vlaky.getString(9), results_vlaky.getString(10), results_vlaky.getString(11),
							results_vlaky.getString(12), results_vlaky.getString(13), results_vlaky.getString(14), "",
							results_vlaky.getString(16), results_vlaky.getString(17) });
				}
				writer_vlaky.writeAll(data_vlaky);
				// closing
				writer_vlaky.close();
				select_vlaky.close();
			}

			// Creating Zip File for Each Operator
			String period = report_period;
			try {
				// adding operator to operators_to_email.txt
				upload_list.add(entry.getKey() + period + ".zip");

				// let's create a ZIP file to write data
				FileOutputStream fos = new FileOutputStream(entry.getKey() + period + ".zip");
				ZipOutputStream zipOS = new ZipOutputStream(fos);

				String file1 = file_name_verze;
				String file2 = file_name_dopravci;
				String file3 = file_name_jizdenky;

				writeToZipFile(file1, zipOS, entry.getKey());
				writeToZipFile(file2, zipOS, entry.getKey());
				writeToZipFile(file3, zipOS, entry.getKey());

				zipOS.close();
				fos.close();

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} // end of for loop

		// deleting extra verze, dopravci, and jizdenky csv files
		ArrayList<String> files = new ArrayList<String>();
		files.add(file_name_dopravci);
		files.add(file_name_jizdenky);
		files.add(file_name_verze);
		for (String f : files) {
			File file = new File(f);
			file.delete();
		}



		System.out.println("\nDone\n");


		// uploading zip files to transfer-sh and then sending slack message 
		for (String s : upload_list) {
			System.out.println("Uploading " + s + " to transfer-sh and sending notification to Slack");
			MediaType MEDIA_TYPE_ZIP = MediaType.parse("application/octet-stream");

			OkHttpClient client = new OkHttpClient();
			File zipFile = new File(s);
			RequestBody requestBody = RequestBody.create(MEDIA_TYPE_ZIP, zipFile);  
			Request request = new Request.Builder()
					.url("https://transfer-sh.bileto.com/cis_dailybus_06_2019.zip")
					.put(requestBody)
					.addHeader("Authorization", "Basic b25ib2FyZDpiYWNrdXA=")
					.addHeader("Content-Type", "application/octet-stream")
					.addHeader("Accept", "*/*")
					.addHeader("Cache-Control", "no-cache")
					.addHeader("Host", "transfer-sh.bileto.com")
					.addHeader("accept-encoding", "gzip, deflate")
					.addHeader("content-length", "331186")
					.addHeader("Connection", "keep-alive")
					.addHeader("cache-control", "no-cache")
					.build();

			Response response = client.newCall(request).execute();
			String output = response.body().string();

			// Sending message to slack channel 
			OkHttpClient client2 = new OkHttpClient();
			MediaType mediaType = MediaType.parse("application/json");
			RequestBody body = RequestBody.create(mediaType, "{ \"text\" : \""+ s + " has been uploaded and is ready for viewing at " + output + "\" }");
			Request request2 = new Request.Builder()
					.url("https://hooks.slack.com/services/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")
					.post(body)
					.addHeader("asdf", "asdf")
					.addHeader("Content-Type", "application/json")
					.addHeader("User-Agent", "PostmanRuntime/7.15.0")
					.addHeader("Accept", "*/*")
					.addHeader("Cache-Control", "no-cache")
					.addHeader("Postman-Token", "a342b570-82be-48a4-85a4-a44b227a3df2,5685ed9c-4890-4a13-984e-b4b8a25380a0")
					.addHeader("Host", "hooks.slack.com")
					.addHeader("accept-encoding", "gzip, deflate")
					.addHeader("content-length", "19")
					.addHeader("Connection", "keep-alive")
					.addHeader("cache-control", "no-cache")
					.build();
			Response response2 = client2.newCall(request2).execute();

			// deleting zip files from local directory 
			File file = new File(s);
			file.delete();

		}	

		System.out.println("\nReporting Complete");

	}

	// method for writing to zip file
	public static void writeToZipFile(String path, ZipOutputStream zipStream, String key)
			throws FileNotFoundException, IOException {

		System.out.println("Writing file : '" + path + "' to " + key + " zip file");

		File aFile = new File(path);
		FileInputStream fis = new FileInputStream(aFile);
		ZipEntry zipEntry = new ZipEntry(path);
		zipStream.putNextEntry(zipEntry);

		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipStream.write(bytes, 0, length);
		}

		zipStream.closeEntry();
		fis.close();
	}

}
