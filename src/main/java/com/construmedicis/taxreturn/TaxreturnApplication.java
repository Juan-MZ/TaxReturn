package com.construmedicis.taxreturn;

import com.construmedicis.taxreturn.gui.MainUI;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaxreturnApplication {

	public static void main(String[] args) {
        // Lanzamos JavaFX (que a su vez lanzará Spring)
        Application.launch(MainUI.class, args);
	}

}
