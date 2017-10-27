package no.rutebanken.anshar.routes.siri;

import com.google.common.base.Preconditions;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.Siri;

public class SiriRequestFactory {

	private SubscriptionSetup subscriptionSetup;

	public static String getCamelUrl(String url) {
		return getCamelUrl(url, null);
	}
	public static String getCamelUrl(String url, String parameters) {
		Preconditions.checkNotNull(url);

		if (parameters != null && !parameters.isEmpty()) {
			String separator = "?";
			if (url.contains("?")) {
				separator = "&";
			}
			if (parameters.startsWith("?")) {
				parameters = parameters.substring(1);
			}
			url = url + separator + parameters;
		}

		if (url.startsWith("https4://")) {
			return url;
		}
		return "http4://" + url;
	}

	public SiriRequestFactory(SubscriptionSetup subscriptionSetup) {
		this.subscriptionSetup = subscriptionSetup;
	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriSubscriptionRequest() {
		return SiriObjectFactory.createSubscriptionRequest(subscriptionSetup);
	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriTerminateSubscriptionRequest() {
		return SiriObjectFactory.createTerminateSubscriptionRequest(subscriptionSetup);

	}

	/*
	 * Called dynamically from camel-routes
	 */
	public Siri createSiriCheckStatusRequest() {
		return SiriObjectFactory.createCheckStatusRequest(subscriptionSetup);

	}

	private Boolean allData = Boolean.TRUE;

	/*
	 * Called dynamically from camel-routes
	 *
	 * Creates ServiceRequest or DataSupplyRequest based on subscription type
	 */
	public Siri createSiriDataRequest() {
		Siri request = null;
		if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY) {
			request = SiriObjectFactory.createDataSupplyRequest(subscriptionSetup, allData);
			allData = Boolean.FALSE;
		} else {
			request = SiriObjectFactory.createServiceRequest(subscriptionSetup);
		}

		return request;
	}

}
