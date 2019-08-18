package net.java.sip.communicator.plugin.portforward;

import static net.java.sip.communicator.plugin.portforward.PortForwardUtils.parseAddressString;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class ForwardConfig
{

    private final String name;

    private InetSocketAddress address;

    private List<String> contactNames;

    private boolean listen;

    public ForwardConfig(boolean listen, Properties props, String prefix, String name)
        throws Exception
    {
        this.name = Objects.requireNonNull(name);
        this.listen = listen;
        Builder builder = new Builder();
        builder.props = Objects.requireNonNull(props);
        builder.prefix = Objects.requireNonNull(prefix);
        builder.build();
    }

    public String getName() {
        return name;
    }


    public InetSocketAddress getAddress()
    {
        return address;
    }

    /**
     * @return should we listen, default true
     */
    public boolean isListen()
    {
        return listen;
    }

    private class Builder
    {
        private String prefix;
        private Properties props;

        private void build() throws Exception
        {
            parseAddress();
            parseContact();
        }

        private void parseContact()
        {
            String sContacts = getProp("contacts");
            Objects.requireNonNull(sContacts);
            String[] a = sContacts.split(" ");
            if (a.length == 1 && a[0].isEmpty()) {
            	a = new String[0];
            }
            contactNames = Arrays.asList(a);
        }

        private void parseAddress() throws URISyntaxException
        {
            address = parseAddressString(getProp("address"), 0);
        }

        /**
         * 
         * @param prop
         * @return true or false if and only if the string value is exactly
         *         "true" or "false", otherwise null
         */
        private Boolean getBoolean(String prop)
        {
            String s = getProp(prop);
            return Boolean.toString(false).equalsIgnoreCase(s) ? Boolean.FALSE
                : Boolean.toString(true).equalsIgnoreCase(s) ? Boolean.TRUE
                    : null;
        }

        private String getProp(String prop)
        {
            return props
                .getProperty(prefix + name + "." + prop);
        }
    }

    public List<String> getContactNames()
    {
        return contactNames;
    }

    public void setContactNames(List<String> contactNames)
    {
        this.contactNames = contactNames;
    }

}
