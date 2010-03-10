/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.core.eventhandler.annotation.postprocessor;

import net.sf.cglib.proxy.Enhancer;
import org.axonframework.core.Event;
import org.axonframework.core.StubDomainEvent;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.annotation.AnnotationEventListenerAdapter;
import org.axonframework.core.eventhandler.annotation.AsynchronousEventListener;
import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.junit.*;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationEventListenerBeanPostProcessorTest {

    private AnnotationEventListenerAdapter mockAdapter;

    private AnnotationEventListenerBeanPostProcessor testSubject;
    private EventBus mockEventBus;
    private ApplicationContext mockApplicationContext;

    @Before
    public void setUp() {
        mockAdapter = mock(AnnotationEventListenerAdapter.class);
        mockEventBus = mock(EventBus.class);
        mockApplicationContext = mock(ApplicationContext.class);
        testSubject = new AnnotationEventListenerBeanPostProcessor();
        testSubject.setEventBus(mockEventBus);
        testSubject.setApplicationContext(mockApplicationContext);
    }

    @Test
    public void testAdapt_WithExecutor() {
        StubExecutor mockExecutor = new StubExecutor();
        testSubject.setExecutor(mockExecutor);

        EventListener actualResult = (EventListener) testSubject.postProcessAfterInitialization(new AsyncHandler(),
                                                                                                "beanName");
        actualResult.handle(new StubDomainEvent());
        assertEquals(1, mockExecutor.invocationCounter);
    }

    @Test
    public void testEventBusIsNotAutowiredWhenProvided() throws Exception {

        testSubject.afterPropertiesSet();

        verify(mockApplicationContext, never()).getBeansOfType(EventBus.class);
    }

    @Test
    public void testEventBusIsAutowired() throws Exception {
        testSubject.setEventBus(null);
        Map<String, EventBus> map = new HashMap<String, EventBus>();
        map.put("ignored", mockEventBus);
        when(mockApplicationContext.getBeansOfType(EventBus.class)).thenReturn(map);

        testSubject.afterPropertiesSet();

        verify(mockApplicationContext).getBeansOfType(EventBus.class);
    }

    @Test
    public void testEventHandlerCallsRedirectToAdapter() {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(Enhancer.isEnhanced(postProcessedBean.getClass()));
        assertTrue(postProcessedBean instanceof EventListener);
        assertTrue(postProcessedBean instanceof SyncEventListener);

        EventListener eventListener = (EventListener) postProcessedBean;
        SyncEventListener annotatedEventListener = (SyncEventListener) postProcessedBean;
        StubDomainEvent domainEvent = new StubDomainEvent();
        eventListener.handle(domainEvent);

        assertEquals(1, annotatedEventListener.getInvocationCount());

    }

    @Test
    public void testEventHandlerAdapterIsInitializedAndDestroyedProperly() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        EventListener postProcessedBean = (EventListener) testSubject.postProcessAfterInitialization(result1,
                                                                                                     "beanName");

        verify(mockEventBus).subscribe(isA(EventListener.class));

        verify(mockEventBus, never()).unsubscribe(isA(EventListener.class));

        testSubject.postProcessBeforeDestruction(postProcessedBean, "beanName");

        verify(mockEventBus).unsubscribe(isA(EventListener.class));
    }

    public static class SyncEventListener {

        private int invocationCount;

        @EventHandler
        public void handleEvent(Event event) {
            invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    public static class StubExecutor implements Executor {

        private int invocationCounter = 0;

        @Override
        public void execute(Runnable command) {
            this.invocationCounter++;
        }
    }

    @AsynchronousEventListener
    public static class AsyncHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }
    }
}
