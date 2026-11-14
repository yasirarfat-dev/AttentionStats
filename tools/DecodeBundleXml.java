import com.android.aapt.Resources;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Decode one bounded AAB protobuf XML resource using the already pinned bundletool. */
class DecodeBundleXml {
    static Element element(Document document, Resources.XmlNode node, int depth) {
        if (!node.hasElement() || depth > 16) throw new IllegalArgumentException("Unexpected XML node/depth");
        var source = node.getElement();
        var result = document.createElementNS(source.getNamespaceUri().isEmpty() ? null : source.getNamespaceUri(), source.getName());
        for (var attr : source.getAttributeList()) {
            String value = attr.getValue();
            if (attr.hasCompiledItem() && attr.getCompiledItem().hasPrim()) {
                var primitive = attr.getCompiledItem().getPrim();
                if (primitive.getOneofValueCase() == Resources.Primitive.OneofValueCase.BOOLEAN_VALUE)
                    value = Boolean.toString(primitive.getBooleanValue());
            }
            if (result.hasAttributeNS(attr.getNamespaceUri().isEmpty() ? null : attr.getNamespaceUri(), attr.getName()))
                throw new IllegalArgumentException("Duplicate XML attribute");
            result.setAttributeNS(attr.getNamespaceUri().isEmpty() ? null : attr.getNamespaceUri(), attr.getName(), value);
        }
        for (var child : source.getChildList()) {
            if (child.getNodeCase() == Resources.XmlNode.NodeCase.TEXT && child.getText().isBlank()) continue;
            result.appendChild(element(document, child, depth + 1));
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("base/res/[A-Za-z0-9_./-]+\\.xml") || args[1].contains(".."))
            throw new IllegalArgumentException("Expected bundle and resource path");
        try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
            var entry = zip.getEntry(args[1]);
            if (entry == null || entry.getSize() < 0 || entry.getSize() > 65536)
                throw new IllegalArgumentException("Missing/oversized XML resource");
            byte[] bytes;
            try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(65537); }
            if (bytes.length > 65536) throw new IllegalArgumentException("Oversized XML resource");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            document.appendChild(element(document, Resources.XmlNode.parseFrom(bytes), 0));
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(System.out));
        }
    }
}
