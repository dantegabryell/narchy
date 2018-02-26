/*
 * Copyright 2013-2014 Richard M. Hightower
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * __________                              _____          __   .__
 * \______   \ ____   ____   ____   /\    /     \ _____  |  | _|__| ____    ____
 *  |    |  _//  _ \ /  _ \ /    \  \/   /  \ /  \\__  \ |  |/ /  |/    \  / ___\
 *  |    |   (  <_> |  <_> )   |  \ /\  /    Y    \/ __ \|    <|  |   |  \/ /_/  >
 *  |______  /\____/ \____/|___|  / \/  \____|__  (____  /__|_ \__|___|  /\___  /
 *         \/                   \/              \/     \/     \/       \//_____/
 *      ____.                     ___________   _____    ______________.___.
 *     |    |____ ___  _______    \_   _____/  /  _  \  /   _____/\__  |   |
 *     |    \__  \\  \/ /\__  \    |    __)_  /  /_\  \ \_____  \  /   |   |
 * /\__|    |/ __ \\   /  / __ \_  |        \/    |    \/        \ \____   |
 * \________(____  /\_/  (____  / /_______  /\____|__  /_______  / / ______|
 *               \/           \/          \/         \/        \/  \/
 */

package org.boon.criteria;

import org.boon.core.reflection.BeanUtils;
import org.boon.core.reflection.fields.FieldAccess;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Collects primitive values from  Data repo results.
 * It avoids the creation of many wrapper objects.
 * It also provides a wicked fast version of List<PRIMITIVE> which can do
 * mean, median, standard deviation, sum, etc. over the returned results.
 */
public class IntCollector extends Selector {

    /**
     * Factory for int collector.
     * @param propertyName name of property to collect
     * @return new values
     */
    public static IntCollector intCollector(String propertyName) {
       return new IntCollector(propertyName);
    }

    private IntArrayList list;

    public IntCollector(String fieldName) {
        super(fieldName);
    }

    @Override
    public void handleRow(int index, Map<String, Object> row, Object item, Map<String, FieldAccess> fields) {
        int value;
        if (path) {
            value = BeanUtils.idxInt(item, this.name);
        } else {
            value = fields.get(name).getInt(item);
        }
        list.add( value );
    }

    @Override
    public void handleStart(Collection<?> results) {
       list = new IntArrayList(results.size());


    }

    @Override
    public void handleComplete(List<Map<String, Object>> rows) {

    }

    public IntArrayList list() {
        return list;
    }
}
