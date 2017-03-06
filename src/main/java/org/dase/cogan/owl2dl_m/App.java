package org.dase.cogan.owl2dl_m;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLRendererException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class App extends Application
{
	private FileChooser			fc;
	private DirectoryChooser	dc;

	private Stage				primaryStage;

	private TextField			inputField;
	private TextField			outputField;
	private List<File>			files;
	private File				outputDir;

	private TextArea			log;

	public static void main(String[] args)
	{
		launch();
	}

	@Override
	public void start(Stage stage)
	{
		// Private Fields
		this.primaryStage = stage;
		this.fc = new FileChooser();
		fc.setTitle("Choose OWL Files");
		this.dc = new DirectoryChooser();
		dc.setTitle("Choose Output Folder");

		// Stage
		GridPane gui = new GridPane();
		gui.setPadding(new Insets(10));

		// Input
		Label instructions = new Label("Browse to Input OWL Files: ");
		this.inputField = new TextField();
		this.inputField.setEditable(false);
		Button openButton = new Button("Browse");
		openButton.setOnAction(new EventHandler<ActionEvent>()
		{
			public void handle(ActionEvent arg0)
			{
				// Get Files to open
				files = fc.showOpenMultipleDialog(primaryStage);

				if(files != null)
				{
					String fileString = "";

					for(File f : files)
					{
						fileString += "\"";
						fileString += f.getName();
						fileString += "\" ";
					}

					inputField.setText(fileString);
				}
			}
		});

		gui.add(instructions, 1, 1);
		gui.add(inputField, 2, 1);
		gui.add(openButton, 3, 1);

		// Output
		Label outputInstruct = new Label("Browse to an Output Directory: ");
		this.outputField = new TextField();
		this.outputField.setEditable(false);
		Button outputButton = new Button("Browse");
		outputButton.setOnAction(new EventHandler<ActionEvent>()
		{
			public void handle(ActionEvent arg0)
			{
				// Get the output directory
				outputDir = dc.showDialog(primaryStage);
				// If one is chosen, make sure to set the text for transparency
				if(outputDir != null)
				{
					outputField.setText(outputDir.getPath());
				}
			}
		});

		gui.add(outputInstruct, 1, 2);
		gui.add(outputField, 2, 2);
		gui.add(outputButton, 3, 2);
		////////////
		// Add Log
		this.log = new TextArea();
		this.log.setEditable(false);
		gui.add(log, 1, 3, 2, 1);
		////////////
		// Add confirm/exit button
		VBox dialog = new VBox();
		Button convertButton = new Button("Convert");
		convertButton.setOnAction(new EventHandler<ActionEvent>()
		{
			public void handle(ActionEvent arg0)
			{
				if(!outputField.getText().equals(""))
				{
					Task<Void> task = new Task<Void>()
					{
						@Override
						protected Void call() throws Exception
						{
							convertFiles(files);
							log.appendText("\nJob Completed!\n");
							return null;
						}
					};

					(new Thread(task)).start();

				}
				else
				{
					Alert alert = new Alert(AlertType.ERROR);
					alert.setTitle("Error");
					alert.setHeaderText("Missing Output Directory");
					alert.setContentText("Don't forget to browse to an output directory.");

					alert.showAndWait();
				}
			}
		});
		Button exitButton = new Button("Close");
		exitButton.setOnAction(new EventHandler<ActionEvent>()
		{
			public void handle(ActionEvent arg0)
			{
				Platform.exit();
				System.exit(0);
			}
		});

		dialog.getChildren().addAll(convertButton, exitButton);
		gui.add(dialog, 3, 3);

		// Add padding to each cell in grid
		gui.getChildren().forEach(c -> {
			GridPane.setMargin(c, new Insets(10));
		});
		// Set the stage!
		Scene root = new Scene(gui); // , 400, 200);
		primaryStage.setScene(root);
		primaryStage.show();
	}

	public void convertFiles(List<File> files)
	{

		MyLatexRenderer latex = new MyLatexRenderer();

		for(File file : files)
		{
			try
			{
				// Load the Ontology
				OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

				// Force silent import errors. (ESP wrt purl.org)
				manager.setOntologyLoaderConfiguration(manager.getOntologyLoaderConfiguration()
				        .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT));

				// Load Ontology
				IRI iri = IRI.create(file.toURI());
				// Update Log
				String updateMessage = "Start Processing: " + iri + "\n";
				Platform.runLater(new Runnable()
				{
					public void run()
					{
						log.appendText(updateMessage);
					}
				});

				OWLOntology ontology = manager.loadOntologyFromOntologyDocument(iri);

				Platform.runLater(new Runnable()
				{
					public void run()
					{
						log.appendText("\tLoaded.\n");
					}
				});

				try
				{
					// Write to a Temporary File
					Path p = Files.createTempFile("temp", "");
					PrintWriter tpw = new PrintWriter(p.toFile());
					latex.render(ontology, tpw);
					tpw.close();

					// Update Log
					Platform.runLater(new Runnable()
					{
						public void run()
						{
							log.appendText("\tRendered.\n");
						}
					});

					// Do post processing
					Platform.runLater(new Runnable()
					{
						public void run()
						{
							log.appendText("\tStarted Post-processing.\n");
						}
					});

					// Write the ontology to LaTex conversion
					// Prepare permanent output file
					String outputfile = outputDir.getPath() + File.separatorChar + stripExt(file.getName()) + ".tex";
					File ofile = new File(outputfile);
					PrintWriter pw = new PrintWriter(ofile);
					// Get a scanner to the temp file
					Scanner reader = new Scanner(p.toFile());
					while(reader.hasNextLine())
					{
						String line = reader.nextLine();
						pw.println(splitLine(line));
					}

					// Clean up
					pw.close();
					reader.close();

					Platform.runLater(new Runnable()
					{
						public void run()
						{
							log.appendText("\tFinished Post-processing.\n");
						}
					});
				}
				catch(IOException e)
				{
					System.out.println("IO Failure.");
				}

			}
			catch(OWLOntologyCreationException e)
			{
				System.out.println("Could not create ontology from: " + file);
			}
			catch(OWLRendererException e)
			{
				System.out.println("Could not render ontology from " + file);
			}
		}

	}

	public static String splitLine(String s)
	{
		if(getLineLength(s) > 125)
		{
			// Wrap in multiline environment
			String construct = "\\begin{split}\n";
			// Find a reasonable split point
			construct += findSplit(s);
			// Exit multiline
			construct += "\n\\end{split}";

			s = construct;
		}

		return s;
	}

	public static String findSplit(String s)
	{
		// Find a reasonable split point
		String regex = ",|\\\\sqcap|\\\\sqcup";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(s);
		m.region(100, s.length());
		String construct = "";
		if(m.find())
		{
			construct += s.substring(0, m.start());
			construct += "\\\\&\n";
			String sub = s.substring(m.start());
			construct += (sub.length() > 125) ? findSplit(sub) : sub;
		}
		else
		{
			construct += s;
		}
		return construct;
	}

	public static int getLineLength(String s)
	{
		// A list of escaped symbol sequences
		String[] symbols = { "\\\\sqcap", "\\\\sqcup", "\\\\lnot", "\\\\forall", "\\\\exists", "hasValue", "\\\\geq",
		        "\\\\leq", ">", "<", "=", "&\\\\sqsubseteq", "&\\\\equiv", "&\\\\not\\\\equiv", "\\\\top", "\\\\bot",
		        "\\\\circ", "\\^-" };

		// Replace all latex symbols
		for(String sym : symbols)
		{
			s = s.replaceAll(sym, "1");
		}
		// Replace quotes and carats and self sequence
		s = s.replaceAll("``", "\"").replaceAll("\\\\\\^\\{\\}", "^").replaceAll("''", "\"")
		        .replaceAll("\\\\textsf\\{Self\\}", "self");
		// Extract contents of \text{.*} and replace with capture
		String regex = "\\\\text\\{(.*?)\\}";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(s);
		ArrayList<String> matches = new ArrayList<>();
		while(m.find())
		{
			matches.add(m.group(1));
		}
		for(String r : matches)
		{
			s = s.replaceFirst(regex, r);
		}
		// Finally strip all remaining backslashes
		s = s.replace("\\", "");

		return s.length();
	}

	/**
	 * Bad method simply strips off last four characters '.owl'
	 * 
	 * @param filename
	 * @return
	 */
	public static String stripExt(String filename)
	{
		return filename.substring(0, filename.length() - 4);
	}
}
