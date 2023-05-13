package ui

import CollectionHandler
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList

object CollectionWrappers
{
    class CollectionWrapper(val obj: CollectionHandler.Collection, val nameProperty: StringProperty)
    {
        init
        {
            nameProperty.addListener { _, _, newVal ->
                obj.name = newVal
            }
        }

        override fun toString(): String {
            return nameProperty.get()
        }
    }

    private fun extractor() = { c: CollectionWrapper ->
        arrayOf(c.nameProperty)
    }

    val wrappers: ObservableList<CollectionWrapper> = FXCollections.observableArrayList(extractor())

    init
    {
        CollectionHandler.addOnCollectionsChanged(CollectionWrappers::refresh)
    }

    fun refresh()
    {
        wrappers.clear()
        wrappers.addAll(CollectionHandler.getCollections { collections ->
            collections.map {
                CollectionWrapper(it, SimpleStringProperty(it.name))
            }
        })
    }
}