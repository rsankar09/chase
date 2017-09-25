(function (document, $, ns) {
    "use strict";
 
    $(document).on("click", ".cq-dialog-submit", function (e) {
    	var $form = $(this).closest("form.foundation-form");
    	var $field = $form.find("[name='./externalScriptUrls']");
    	if ($field.length < 1) {
    		return;
    	}
    	
    	e.stopPropagation();
        e.preventDefault();
        
        var valid = true;
        var clazz = "coral-Button ";
        var patterns = {
             jsURLadd: /^(http(s)?:\/\/)(([da-z.-]+).([a-z.]{2,6}))((\/)([A-Za-z0-9_\-]+))+((\.(js)){1})$/i
        };
 
        $field.each(function(index, element) {
        	if(element.value != "" && !patterns.jsURLadd.test(element.value) && (element.value != null)) {
        	    valid = false;
        	}
        });
 
        if(valid) {
        	$form.submit();
        } else {
            ns.ui.helpers.prompt({
            	title: Granite.I18n.get("Invalid Input"),
            	message: "External JavaScript must be valid URLs to JavaScript file.",
            	actions: [{
            		id: "CANCEL",
            		text: "OK",
            		className: "coral-Button"
            	}],
            	callback: function (actionId) {
            		if (actionId === "CANCEL") {
            		}
            	}
            });
           
            e.stopPropagation();
            e.preventDefault();
            return false;
        }
    });
})(document, Granite.$, Granite.author);