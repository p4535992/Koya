/**
 * Koya is an alfresco module that provides a corporate orientated dataroom.
 *
 * Copyright (C) Itl Developpement 2014
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.itldev.koya.model;

import java.io.IOException;

import org.alfresco.service.cmr.repository.NodeRef;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonSubTypes.Type;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

import fr.itldev.koya.model.impl.Company;
import fr.itldev.koya.model.impl.Directory;
import fr.itldev.koya.model.impl.Document;
import fr.itldev.koya.model.impl.Dossier;
import fr.itldev.koya.model.impl.SalesOffer;
import fr.itldev.koya.model.impl.Space;
import fr.itldev.koya.model.impl.Template;
import fr.itldev.koya.model.impl.User;
import fr.itldev.koya.model.interfaces.AlfrescoNode;
import fr.itldev.koya.services.impl.util.NodeRefDeserializer;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
    @Type(value = Company.class, name = "company"),
    @Type(value = Space.class, name = "space"),
    @Type(value = Dossier.class, name = "dossier"),
    @Type(value = Directory.class, name = "directory"),
    @Type(value = Document.class, name = "document"),
    @Type(value = SalesOffer.class, name = "salesoffer"),
    @Type(value = Template.class, name = "template"),
    @Type(value = User.class, name = "user")})
public abstract class SecuredItem implements AlfrescoNode{

    //fields that should be escpaed before serialization
    // public final static String[] ESCAPED_FIELDS_NAMES = {"name", "title"};
    private NodeRef nodeRef;
    private String name;
    private String title;

    // <editor-fold defaultstate="collapsed" desc="Getters/Setters">
    @Override
    @JsonDeserialize(using = NodeRefDeserializer.class)
    public NodeRef getNodeRef() {
        return nodeRef;
    }

    public void setNodeRef(NodeRef nodeRef) {
        this.nodeRef = nodeRef;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getTitle() {
        if (title == null || title.isEmpty()) {
            return name;
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // </editor-fold>
    public SecuredItem() {
    }

    public SecuredItem(NodeRef nodeRef, String name) {
        this.nodeRef = nodeRef;
        this.name = name;
    }

    private static final Integer HASHCONST1 = 3;
    private static final Integer HASHCONST2 = 47;

    @Override
    public int hashCode() {
        int hash = HASHCONST1;
        hash = HASHCONST2 * hash + (getNodeRef() != null ? getNodeRef().hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SecuredItem other = (SecuredItem) obj;
        if ((getNodeRef() == null) ? (other.getNodeRef() != null) : !this.getNodeRef().equals(other.getNodeRef())) {
            return false;
        }
        return true;
    }

    @JsonIgnore
    public String getAsJSON() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }

    /**
     * Useful method to deserialize content.
     *
     * @return
     */
    public abstract String getType();

    /**
     * Implemented for deserialization compatibility
     *
     * @param contentType
     */
    public void setType(String contentType) {
    }

}
