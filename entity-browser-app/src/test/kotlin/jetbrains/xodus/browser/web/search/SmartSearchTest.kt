package jetbrains.xodus.browser.web.search

import jetbrains.exodus.entitystore.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.runners.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SmartSearchTest {
    private val TYPE_ID = 0
    private val TYPE_NAME = "type"

    @Mock
    private lateinit var tx: StoreTransaction

    @Mock
    private lateinit var iterable: EntityIterable

    @Mock
    private lateinit var union: EntityIterable

    @Mock
    private lateinit var intersection: EntityIterable

    @Mock
    private lateinit var entity: Entity

    @Before
    fun construct() {
        `when`(iterable.union(any(EntityIterable::class.java))).thenReturn(union)
        `when`(union.union(any(EntityIterable::class.java))).thenReturn(union)

        `when`(iterable.intersect(any(EntityIterable::class.java))).thenReturn(intersection)
        `when`(intersection.intersect(any(EntityIterable::class.java))).thenReturn(intersection)
    }

    @Test
    fun testSearchAll() {
        `when`(tx.getAll(TYPE_NAME)).thenReturn(iterable)
        val search = search(null)
        assertEquals(iterable, search)
        verify(tx).getAll(eq(TYPE_NAME))
    }

    @Test
    fun testSimpleIdSearch() {
        val id = PersistentEntityId(TYPE_ID, 1)
        `when`(tx.getEntity(id)).thenReturn(entity)
        `when`(tx.getSingletonIterable(eq(entity))).thenReturn(iterable)

        val search = search("1")
        assertEquals(iterable, search)
        verify(tx).getEntity(eq(id))
        verify(tx).getSingletonIterable(eq(entity))
    }

    @Test
    fun testWrongIdSearch() {
        val search = search("1x")
        assertNotNull(search)
        assertEquals(0, search.size())
        verify(tx, never()).getEntity(any(PersistentEntityId::class.java))
    }

    @Test
    fun testSearchByIdParam() {
        val id = PersistentEntityId(TYPE_ID, 1)
        `when`(tx.getEntity(id)).thenReturn(entity)
        `when`(tx.find(TYPE_NAME, "id", "1")).thenReturn(iterable)
        `when`(tx.getSingletonIterable(eq(entity))).thenReturn(iterable)

        val search = search("id=1")
        assertEquals(union, search)
        verify(tx).getEntity(eq(id))
        verify(tx).getSingletonIterable(eq(entity))
        verify(tx).find(eq(TYPE_NAME), eq("id"), eq("1"))
    }

    @Test
    fun testSearchByIdRange() {
        `when`(tx.findIds(TYPE_NAME, 1, 10)).thenReturn(iterable)
        `when`(tx.find(TYPE_NAME, "id", 1L, 10L)).thenReturn(iterable)
        `when`(tx.getSingletonIterable(eq(entity))).thenReturn(iterable)

        val search = search("id=[1,10]")
        assertEquals(union, search)
        verify(tx).findIds(eq(TYPE_NAME), eq(1L), eq(10L))
        verify(tx).find(eq(TYPE_NAME), eq("id"), eq(1L), eq(10L))
    }

    @Test
    fun testSearchByLike() {
        `when`(tx.findStartingWith(TYPE_NAME, "firstName", "Jo")).thenReturn(iterable)

        val search = search("firstName~Jo")
        assertEquals(iterable, search)
        verify(tx).findStartingWith(eq(TYPE_NAME), eq("firstName"), eq("Jo"))
    }

    @Test
    fun testSearchByParams() {
        `when`(tx.findStartingWith(TYPE_NAME, "firstName", "Jo")).thenReturn(iterable)
        `when`(tx.find(TYPE_NAME, "lastName", "McClane")).thenReturn(iterable)
        `when`(tx.getAll(TYPE_NAME)).thenReturn(iterable)
        `when`(iterable.reverse()).thenReturn(iterable)
        `when`(iterable.take(Matchers.anyInt())).thenReturn(iterable)
        `when`(iterable.iterator()).thenReturn(SingleEntityIterator(entity))
        `when`(entity.propertyNames).thenReturn(listOf("firstName", "lastName"))


        val search = search("firstName~Jo and lastName=McClane")
        assertEquals(intersection, search)
        verify(tx).findStartingWith(eq(TYPE_NAME), eq("firstName"), eq("Jo"))
        verify(tx).find(eq(TYPE_NAME), eq("lastName"), eq("McClane"))
    }

    private fun search(term: String?): EntityIterable {
        return smartSearch(term, TYPE_NAME, TYPE_ID, tx)
    }

    private class SingleEntityIterator(val entity: Entity) : EntityIterator {
        private var hasNext = true
        override fun next(): Entity = entity.apply { hasNext = false }
        override fun nextId(): EntityId? = if (hasNext) entity.id else null
        override fun skip(p0: Int): Boolean = true
        override fun shouldBeDisposed(): Boolean = false
        override fun hasNext(): Boolean = hasNext
        override fun remove() { }
        override fun dispose(): Boolean = true
    }
}